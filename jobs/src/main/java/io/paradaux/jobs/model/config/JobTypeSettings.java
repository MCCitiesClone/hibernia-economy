package io.paradaux.jobs.model.config;


import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * A grouping of jobs — {@code trades}, {@code government}, {@code licenses},
 * {@code qualifications} and so on.
 *
 * <p>Types are organisational. They give {@code /jobs} its sections and supply
 * {@code can-manage} selectors and {@code provision} metadata that every job in the
 * type inherits. A licence is mechanically identical to a trade; only presentation
 * differs.</p>
 *
 * @param managedExternally when true, player-run hire and fire are refused for this
 *                          type. The JobsApi, the console and {@code jobs.admin} are
 *                          unaffected — this is for types another plugin owns, such
 *                          as trades awarded by a skills plugin.
 */
public record JobTypeSettings(
        String displayName,
        int order,
        boolean managedExternally,
        Set<String> canManage,
        ProvisionSettings provision,
        Map<String, JobSettings> jobs
) {

    public JobTypeSettings {
        canManage = canManage == null || canManage.isEmpty()
                ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(canManage));
        // Configuration order decides the order jobs appear in a /jobs section and
        // which job wins a duplicate-group clash, so it must survive the copy.
        jobs = jobs == null || jobs.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(jobs));
    }
}
