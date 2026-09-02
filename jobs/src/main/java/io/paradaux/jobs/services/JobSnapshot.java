package io.paradaux.jobs.services;

import io.paradaux.jobs.api.JobCatalog;
import io.paradaux.jobs.api.model.JobDefinition;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.api.model.JobType;
import io.paradaux.jobs.model.config.JobSettings;
import io.paradaux.jobs.model.config.JobTypeSettings;
import io.paradaux.jobs.model.config.JobsSettings;
import io.paradaux.jobs.model.config.ProvisionSettings;
import io.paradaux.jobs.model.config.ReconciliationSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * An immutable, fully-indexed view of {@code jobs.yml}, built once per reload.
 *
 * <p>Everything the hot paths need is precomputed here so that no lookup has to walk
 * the configuration: the reverse group index that turns a player's LuckPerms groups
 * into held jobs, the unambiguous bare keys that let a player type
 * {@code electrician} instead of {@code trades/electrician}, and the sorted
 * suggestion list that tab-completion reads (which matters because
 * {@code ParameterResolver.suggestions()} always runs off the main thread and must
 * touch nothing mutable).</p>
 *
 * <p>Configuration defects are reported at build time rather than at use time, so an
 * operator sees them in the startup log instead of discovering them when a command
 * mysteriously fails.</p>
 */
public final class JobSnapshot implements JobCatalog {

    private final String adminPermission;
    private final ReconciliationSettings reconciliation;
    private final boolean provisionGroups;
    private final boolean showEmptyTypes;

    private final List<JobType> typesInOrder;
    private final Map<String, JobType> typesByKey;
    private final Map<JobId, JobDefinition> definitions;
    private final Map<String, JobId> byGroupLower;
    private final Map<String, JobId> unambiguousBareKeys;
    private final Map<JobId, ProvisionSettings> provisioning;
    private final Set<String> allGroups;
    private final List<String> suggestions;
    private final Map<String, String> listingTypes;

    private JobSnapshot(String adminPermission, ReconciliationSettings reconciliation,
                        boolean provisionGroups, boolean showEmptyTypes,
                        List<JobType> typesInOrder, Map<String, JobType> typesByKey,
                        Map<JobId, JobDefinition> definitions, Map<String, JobId> byGroupLower,
                        Map<String, JobId> unambiguousBareKeys,
                        Map<JobId, ProvisionSettings> provisioning, Set<String> allGroups,
                        List<String> suggestions, Map<String, String> listingTypes) {
        this.adminPermission = adminPermission;
        this.reconciliation = reconciliation;
        this.provisionGroups = provisionGroups;
        this.showEmptyTypes = showEmptyTypes;
        this.typesInOrder = typesInOrder;
        this.typesByKey = typesByKey;
        this.definitions = definitions;
        this.byGroupLower = byGroupLower;
        this.unambiguousBareKeys = unambiguousBareKeys;
        this.provisioning = provisioning;
        this.allGroups = allGroups;
        this.suggestions = suggestions;
        this.listingTypes = listingTypes;
    }

    /** An empty snapshot, used when configuration is missing or failed to load. */
    public static JobSnapshot empty() {
        return new JobSnapshot("jobs.admin", ReconciliationSettings.defaults(), false, false,
                List.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Set.of(), List.of(), Map.of());
    }

    public static JobSnapshot build(JobsSettings settings, Logger log) {
        if (settings == null) {
            log.severe("jobs.yml could not be loaded; no jobs are configured.");
            return empty();
        }

        Map<String, JobType> typesByKey = new LinkedHashMap<>();
        Map<JobId, JobDefinition> definitions = new LinkedHashMap<>();
        Map<String, JobId> byGroup = new HashMap<>();
        Map<JobId, ProvisionSettings> provisioning = new LinkedHashMap<>();
        Map<String, List<JobId>> bareKeys = new HashMap<>();
        Set<String> groups = new TreeSet<>();
        List<JobType> ordered = new ArrayList<>();

        settings.types().forEach((rawTypeKey, typeSettings) -> {
            if (rawTypeKey == null || rawTypeKey.isBlank() || typeSettings == null) {
                return;
            }
            String typeKey = rawTypeKey.trim().toLowerCase(Locale.ROOT);
            Set<String> typeSelectors = validSelectors(typeSettings.canManage(), "type " + typeKey, log);
            ProvisionSettings typeProvision = typeSettings.provision() == null
                    ? ProvisionSettings.empty() : typeSettings.provision();
            warnOnMalformedWeight(typeProvision, "type " + typeKey, log);

            List<JobId> jobIds = new ArrayList<>();
            for (Map.Entry<String, JobSettings> entry : typeSettings.jobs().entrySet()) {
                String rawJobKey = entry.getKey();
                JobSettings job = entry.getValue();
                if (rawJobKey == null || rawJobKey.isBlank() || job == null) {
                    continue;
                }
                String jobKey = rawJobKey.trim().toLowerCase(Locale.ROOT);
                JobId id = JobId.of(typeKey, jobKey);

                // The binder does not pass a map key into its value object, so the
                // key-derived defaults are resolved here rather than in the record.
                String group = JobSettings.isUnset(job.group()) ? jobKey : job.group().trim();
                String displayName = JobSettings.isUnset(job.displayName())
                        ? titleCase(jobKey) : job.displayName();
                String description = JobSettings.isUnset(job.description()) ? "" : job.description();

                String groupLower = group.toLowerCase(Locale.ROOT);
                JobId clash = byGroup.get(groupLower);
                if (clash != null) {
                    // Two jobs sharing one group would make the reverse index lossy:
                    // a player holding the group could not be told which job it is.
                    log.severe("jobs.yml: LuckPerms group '" + group + "' is claimed by both "
                            + clash.qualified() + " and " + id.qualified()
                            + "; keeping " + clash.qualified() + " and ignoring " + id.qualified() + ".");
                    continue;
                }

                Set<String> selectors = new HashSet<>(typeSelectors);
                selectors.addAll(validSelectors(job.canManage(), "job " + id.qualified(), log));

                ProvisionSettings jobProvision = job.provision() == null
                        ? ProvisionSettings.empty() : job.provision();
                warnOnMalformedWeight(jobProvision, "job " + id.qualified(), log);

                definitions.put(id, new JobDefinition(id, displayName, group, description,
                        Set.copyOf(selectors)));
                provisioning.put(id, typeProvision.mergedWith(jobProvision));
                byGroup.put(groupLower, id);
                groups.add(group);
                bareKeys.computeIfAbsent(jobKey, k -> new ArrayList<>()).add(id);
                jobIds.add(id);
            }

            String typeDisplay = JobSettings.isUnset(typeSettings.displayName())
                    ? titleCase(typeKey) : typeSettings.displayName();
            JobType type = new JobType(typeKey, typeDisplay, typeSettings.order(),
                    typeSettings.managedExternally(), jobIds);
            typesByKey.put(typeKey, type);
            ordered.add(type);
        });

        ordered.sort(Comparator.comparingInt(JobType::order).thenComparing(JobType::key));

        Map<String, JobId> unambiguous = new HashMap<>();
        bareKeys.forEach((key, ids) -> {
            if (ids.size() == 1) {
                unambiguous.put(key, ids.get(0));
            }
        });

        // Every qualified name, plus the bare keys that resolve unambiguously.
        Set<String> suggestionSet = new TreeSet<>();
        definitions.keySet().forEach(id -> suggestionSet.add(id.qualified()));
        suggestionSet.addAll(unambiguous.keySet());

        Map<String, String> listing = new LinkedHashMap<>();
        settings.listingCommands().forEach((command, typeKey) -> {
            if (command == null || typeKey == null) {
                return;
            }
            String normalisedCommand = command.trim().toLowerCase(Locale.ROOT);
            String normalisedType = typeKey.trim().toLowerCase(Locale.ROOT);
            if (!typesByKey.containsKey(normalisedType)) {
                log.warning("jobs.yml: listing-commands." + normalisedCommand + " names type '"
                        + normalisedType + "', which is not configured; /" + normalisedCommand
                        + " will report that it is unconfigured.");
                return;
            }
            listing.put(normalisedCommand, normalisedType);
        });

        // Concrete selectors naming a job that does not exist are almost always a typo.
        for (JobDefinition definition : definitions.values()) {
            for (String selector : definition.canManage()) {
                if (CanManageMatcher.isConcrete(selector)
                        && JobId.parseQualified(selector).filter(definitions::containsKey).isEmpty()) {
                    log.warning("jobs.yml: " + definition.id().qualified() + " can-manage selector '"
                            + selector + "' names a job that is not configured.");
                }
            }
        }

        return new JobSnapshot(settings.adminPermission(), settings.reconciliation(),
                settings.provisionGroups(), settings.showEmptyTypes(),
                Collections.unmodifiableList(ordered), Collections.unmodifiableMap(typesByKey),
                Collections.unmodifiableMap(definitions), Collections.unmodifiableMap(byGroup),
                Collections.unmodifiableMap(unambiguous), Collections.unmodifiableMap(provisioning),
                Collections.unmodifiableSet(groups), List.copyOf(suggestionSet),
                Collections.unmodifiableMap(listing));
    }

    // ---- JobCatalog ----

    @Override
    public List<JobType> types() {
        return typesInOrder;
    }

    @Override
    public Optional<JobType> type(String typeKey) {
        return typeKey == null ? Optional.empty()
                : Optional.ofNullable(typesByKey.get(typeKey.trim().toLowerCase(Locale.ROOT)));
    }

    @Override
    public Optional<JobDefinition> job(JobId id) {
        return id == null ? Optional.empty() : Optional.ofNullable(definitions.get(id));
    }

    @Override
    public Optional<JobId> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String trimmed = token.trim().toLowerCase(Locale.ROOT);
        if (trimmed.indexOf('/') >= 0) {
            return JobId.parseQualified(trimmed).filter(definitions::containsKey);
        }
        // A bare key resolves only when exactly one type defines it; an ambiguous key
        // yields empty so the caller can say so rather than silently guessing.
        return Optional.ofNullable(unambiguousBareKeys.get(trimmed));
    }

    /** Whether a bare token names a job defined in more than one type. */
    public boolean isAmbiguousBareKey(String token) {
        if (token == null || token.isBlank() || token.indexOf('/') >= 0) {
            return false;
        }
        String trimmed = token.trim().toLowerCase(Locale.ROOT);
        return !unambiguousBareKeys.containsKey(trimmed)
                && definitions.keySet().stream().anyMatch(id -> id.job().equals(trimmed));
    }

    @Override
    public Optional<JobId> byGroup(String luckPermsGroup) {
        return luckPermsGroup == null ? Optional.empty()
                : Optional.ofNullable(byGroupLower.get(luckPermsGroup.trim().toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<JobId> jobs() {
        return List.copyOf(definitions.keySet());
    }

    @Override
    public List<JobId> jobsOfType(String typeKey) {
        return type(typeKey).map(JobType::jobs).orElseGet(List::of);
    }

    // ---- Snapshot-only accessors ----

    public String adminPermission() {
        return adminPermission;
    }

    public boolean provisionGroups() {
        return provisionGroups;
    }

    /** How often, if at all, to reconcile the mirror against LuckPerms. */
    public ReconciliationSettings reconciliation() {
        return reconciliation;
    }

    public boolean showEmptyTypes() {
        return showEmptyTypes;
    }

    /** Every configured LuckPerms group name, for provisioning and reconciliation. */
    public Set<String> allGroups() {
        return allGroups;
    }

    /** The merged type+job provisioning metadata for a job. */
    public ProvisionSettings provisioning(JobId id) {
        return provisioning.getOrDefault(id, ProvisionSettings.empty());
    }

    /**
     * Tab-completion candidates. Immutable and precomputed — this is the only thing
     * a {@code suggestions()} call may read, since it runs off the main thread.
     */
    public List<String> suggestions() {
        return suggestions;
    }

    /** The type key a dedicated listing command (e.g. {@code licenses}) displays. */
    public Optional<String> listingType(String command) {
        return command == null ? Optional.empty()
                : Optional.ofNullable(listingTypes.get(command.trim().toLowerCase(Locale.ROOT)));
    }

    private static Set<String> validSelectors(Set<String> raw, String owner, Logger log) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> valid = new HashSet<>();
        for (String selector : raw) {
            if (CanManageMatcher.isValidSelector(selector)) {
                valid.add(selector.trim().toLowerCase(Locale.ROOT));
            } else {
                log.warning("jobs.yml: " + owner + " has malformed can-manage selector '"
                        + selector + "'; expected <type>/<job> with optional '*'. Ignoring it.");
            }
        }
        return valid;
    }

    private static void warnOnMalformedWeight(ProvisionSettings provision, String owner, Logger log) {
        if (provision.hasMalformedWeight()) {
            log.warning("jobs.yml: " + owner + " has a non-numeric provision weight '"
                    + provision.weight() + "'; it will not be applied.");
        }
    }

    private static String titleCase(String key) {
        String spaced = key.replace('-', ' ').replace('_', ' ').trim();
        if (spaced.isEmpty()) {
            return key;
        }
        StringBuilder out = new StringBuilder(spaced.length());
        boolean capitalise = true;
        for (char c : spaced.toCharArray()) {
            if (c == ' ') {
                capitalise = true;
                out.append(c);
            } else if (capitalise) {
                out.append(Character.toUpperCase(c));
                capitalise = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
