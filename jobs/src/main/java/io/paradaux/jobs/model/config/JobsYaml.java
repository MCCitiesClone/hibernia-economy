package io.paradaux.jobs.model.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses {@code jobs.yml} into {@link JobsSettings}.
 *
 * <p>Hand-rolled rather than declarative because the published hibernia-framework
 * artifact binds only flat scalar fields out of {@code config.yml}: no nested
 * objects, no lists, no maps, and no per-file components. The monorepo already
 * takes this approach for every configuration with real structure — Treasury's
 * salary and balance-tax configs, Business's firm config — and Business's Guice
 * module explicitly notes that those own their reload-safe snapshots and should not
 * be migrated to the declarative binder.</p>
 *
 * <p>Parsing is total: a malformed or missing section yields defaults rather than an
 * exception, so one bad edit degrades a single job instead of preventing the plugin
 * from starting. Semantic problems (a duplicate group, a dangling selector) are
 * reported by {@code JobSnapshot} once the whole file is in hand.</p>
 */
public final class JobsYaml {

    private JobsYaml() {
    }

    /** Read a whole {@code jobs.yml}. A null root yields fully-defaulted settings. */
    public static JobsSettings parse(ConfigurationSection root) {
        if (root == null) {
            return new JobsSettings("jobs.admin", true, false, Map.of(),
                    ReconciliationSettings.defaults(), Map.of());
        }

        return new JobsSettings(
                root.getString("admin-permission", "jobs.admin"),
                root.getBoolean("provision-groups", true),
                root.getBoolean("show-empty-types", false),
                listingCommands(root.getConfigurationSection("listing-commands")),
                reconciliation(root.getConfigurationSection("reconciliation")),
                types(root.getConfigurationSection("types")));
    }

    private static ReconciliationSettings reconciliation(ConfigurationSection section) {
        if (section == null) {
            return ReconciliationSettings.defaults();
        }
        return new ReconciliationSettings(
                section.getBoolean("enabled", true),
                section.getLong("interval-seconds", 1800L));
    }

    private static Map<String, JobTypeSettings> types(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, JobTypeSettings> types = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection type = section.getConfigurationSection(key);
            if (type == null) {
                continue;
            }
            types.put(key, new JobTypeSettings(
                    type.getString("display-name", JobSettings.UNSET),
                    type.getString("color", JobSettings.UNSET),
                    type.getInt("order", 1000),
                    type.getBoolean("managed-externally", false),
                    stringSet(type, "can-manage"),
                    provision(type.getConfigurationSection("provision")),
                    jobs(type.getConfigurationSection("jobs"))));
        }
        return types;
    }

    private static Map<String, JobSettings> jobs(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, JobSettings> jobs = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection job = section.getConfigurationSection(key);
            if (job == null) {
                continue;
            }
            jobs.put(key, new JobSettings(
                    job.getString("display-name", JobSettings.UNSET),
                    job.getString("group", JobSettings.UNSET),
                    job.getString("description", JobSettings.UNSET),
                    job.getString("color", JobSettings.UNSET),
                    stringSet(job, "can-manage"),
                    provision(job.getConfigurationSection("provision"))));
        }
        return jobs;
    }

    private static ProvisionSettings provision(ConfigurationSection section) {
        if (section == null) {
            return ProvisionSettings.empty();
        }
        // Read weight as a raw string so "declared but not a number" stays
        // distinguishable from "not declared" — getInt would collapse both to 0.
        String weight = section.contains("weight")
                ? String.valueOf(section.get("weight")) : ProvisionSettings.UNSET;
        return new ProvisionSettings(
                weight,
                section.getString("prefix", ProvisionSettings.UNSET),
                section.getString("prefix-color", ProvisionSettings.UNSET),
                List.copyOf(section.getStringList("permissions")));
    }

    private static Set<String> stringSet(ConfigurationSection section, String path) {
        List<String> raw = section.getStringList(path);
        return raw.isEmpty() ? Set.of() : new LinkedHashSet<>(raw);
    }

    /**
     * Parse {@code listing-commands:}, where the key is the command name.
     *
     * <p>Accepts a bare string value ({@code licenses: licenses}) as shorthand for a
     * command with no aliases, so an existing file keeps working, as well as the full
     * block form with {@code type:} and {@code aliases:}.</p>
     */
    private static Map<String, ListingCommandSettings> listingCommands(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, ListingCommandSettings> commands = new LinkedHashMap<>();
        for (String name : section.getKeys(false)) {
            ConfigurationSection block = section.getConfigurationSection(name);
            if (block != null) {
                String type = block.getString("type");
                if (type != null && !type.isBlank()) {
                    commands.put(name, new ListingCommandSettings(
                            type, List.copyOf(block.getStringList("aliases"))));
                }
                continue;
            }
            String type = section.getString(name);
            if (type != null && !type.isBlank()) {
                commands.put(name, new ListingCommandSettings(type, List.of()));
            }
        }
        return commands;
    }
}
