package io.paradaux.jobs.model.config;


/**
 * How often to compare LuckPerms' real membership against this plugin's mirror.
 *
 * <p>The reconciler only ever repairs the mirror and the audit log; it never writes
 * to LuckPerms. See {@code JobReconciliationTask} for why.</p>
 */
public record ReconciliationSettings(
        boolean enabled,
        long intervalSeconds
) {

    public static ReconciliationSettings defaults() {
        return new ReconciliationSettings(true, 1800L);
    }

    public ReconciliationSettings {
        // A zero or negative interval would schedule a task that never fires (or
        // throws); fall back to the default rather than silently disabling.
        if (intervalSeconds <= 0) {
            intervalSeconds = 1800L;
        }
    }
}
