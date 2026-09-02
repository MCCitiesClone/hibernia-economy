package io.paradaux.jobs.permissions;

/**
 * The result of adding or removing a group, mapped from LuckPerms'
 * {@code DataMutateResult}.
 *
 * <p>"They already had it" and "they didn't have it" are ordinary outcomes rather
 * than failures. That matters because an owning plugin re-syncing a job it has
 * already granted is the common case, and it must not produce an error, a redundant
 * save, or a spurious audit row.</p>
 */
public enum MutationOutcome {
    /** The node was added or removed and the change was saved. */
    CHANGED,
    /** The player already held the group; nothing was written. */
    ALREADY_HAD,
    /** The player did not hold the group; nothing was written. */
    DID_NOT_HAVE,
    /** The backend refused or errored. */
    FAILED
}
