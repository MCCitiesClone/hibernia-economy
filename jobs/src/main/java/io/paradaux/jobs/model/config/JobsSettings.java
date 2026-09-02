package io.paradaux.jobs.model.config;


import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * The parsed contents of {@code jobs.yml}.
 *
 * <p>This is a plain record, not a framework configuration component. The published
 * hibernia-framework artifact this module builds against binds only flat scalar
 * fields out of {@code config.yml} — it has no nested-object, list or map support
 * and its {@code @ConfigurationComponent} takes no {@code file} attribute — so a
 * nested structure like this one is parsed by {@link JobsYaml} instead. That also
 * matches how the rest of the monorepo handles configuration with non-trivial shape
 * or custom reload semantics (Treasury's salary and balance-tax configs, Business's
 * firm config), which deliberately own their own loaders.</p>
 *
 * <h2>Do not inject this</h2>
 * <p>A reload produces a new instance, so anything holding a reference from
 * injection time would be pinned to the boot snapshot forever.
 * <strong>{@code JobRegistryImpl} is the only class permitted to reference this
 * type.</strong> It re-reads the file on {@code rebuild()} and publishes an
 * immutable {@code JobSnapshot} behind an {@code AtomicReference}; everything else
 * depends on {@code JobRegistry}. The reload story then lives in exactly one place
 * instead of in every consumer.</p>
 */
public record JobsSettings(
        String adminPermission,
        boolean provisionGroups,
        boolean showEmptyTypes,
        Map<String, ListingCommandSettings> listingCommands,
        ReconciliationSettings reconciliation,
        Map<String, JobTypeSettings> types
) {

    public JobsSettings {
        adminPermission = adminPermission == null || adminPermission.isBlank()
                ? "jobs.admin" : adminPermission.trim();
        listingCommands = ordered(listingCommands);
        // An omitted `reconciliation:` section binds to null (a legitimate "not
        // configured"), so default it rather than NPE-ing at schedule time.
        reconciliation = reconciliation == null ? ReconciliationSettings.defaults() : reconciliation;
        types = ordered(types);
    }

    /**
     * Copy a map preserving iteration order.
     *
     * <p>{@code Map.copyOf} returns an unordered map, which would make configuration
     * order — and therefore section ordering and first-wins resolution of a duplicate
     * group — depend on hash order.</p>
     */
    private static <V> Map<String, V> ordered(Map<String, V> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
