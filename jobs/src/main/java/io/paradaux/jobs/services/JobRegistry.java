package io.paradaux.jobs.services;

/**
 * Provides the current {@link JobSnapshot} and rebuilds it on reload.
 *
 * <p>This is the seam that keeps configuration reloads correct. The framework binds
 * configuration components with {@code toInstance} but publishes a <em>new</em>
 * record on reload, so any class injecting {@code JobsSettings} would be pinned to
 * the boot snapshot forever. Only the implementation of this interface touches that
 * type; everything else depends on this and always sees current configuration.</p>
 */
public interface JobRegistry {

    /** The current snapshot. Immutable — do not cache it across an operation. */
    JobSnapshot snapshot();

    /** Re-read the configuration and publish a new snapshot atomically. */
    void rebuild();
}
