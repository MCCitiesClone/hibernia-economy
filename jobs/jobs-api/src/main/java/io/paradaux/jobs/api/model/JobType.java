package io.paradaux.jobs.api.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A configured grouping of jobs — {@code trades}, {@code government},
 * {@code licenses}, {@code qualifications} and so on.
 *
 * <p>Types are purely organisational: they give {@code /jobs} its sections, supply
 * {@code can-manage} selectors and provisioning metadata that every job in the type
 * inherits, and let {@code /licenses} and {@code /qualifications} filter. A licence
 * is mechanically identical to a trade; only its presentation differs.</p>
 *
 * @param key               the configuration key, lower-cased
 * @param displayName       section header shown by {@code /jobs}
 * @param color             MiniMessage colour tag the section header is drawn in,
 *                          e.g. {@code <aqua>}; empty for the default palette
 * @param order             ascending sort position among sections; ties break on key
 * @param managedExternally when true, player-run hire and fire are refused for this
 *                          type. The API, the console and {@code jobs.admin} are
 *                          unaffected — this exists for types another plugin owns.
 * @param jobs              the jobs in this type, in configuration order
 */
public record JobType(@NotNull String key,
                      @NotNull String displayName,
                      @NotNull String color,
                      int order,
                      boolean managedExternally,
                      @NotNull List<JobId> jobs) {

    public JobType {
        color = color == null ? "" : color;
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
    }

    /** Whether a colour was configured for this type. */
    public boolean hasColor() {
        return !color.isEmpty();
    }
}
