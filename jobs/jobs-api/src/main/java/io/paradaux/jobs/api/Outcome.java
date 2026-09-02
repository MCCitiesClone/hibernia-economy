package io.paradaux.jobs.api;

/**
 * The result of a hire, fire or quit.
 *
 * <p>Ordinary, expected conditions are outcomes rather than exceptions — notably
 * {@link #ALREADY_HELD} and {@link #NOT_HELD}, which come straight from LuckPerms'
 * own {@code DataMutateResult} and are the common case when an external plugin
 * re-syncs a player it has already granted.</p>
 */
public enum Outcome {
    /** The membership changed and was recorded. */
    SUCCESS,
    /** The player already held the job; nothing was written and nothing logged. */
    ALREADY_HELD,
    /** The player did not hold the job; nothing was written and nothing logged. */
    NOT_HELD,
    /** No such job is configured. */
    UNKNOWN_JOB,
    /** The token matched a bare job key present in more than one type. */
    AMBIGUOUS_JOB,
    /** The actor's jobs grant no can-manage selector matching the target. */
    NOT_AUTHORISED,
    /** The type is flagged managed-externally and the actor is an ordinary player. */
    EXTERNALLY_MANAGED,
    /** The group is inherited from a parent rank, so no direct node exists to remove. */
    INHERITED_NOT_DIRECT,
    /** LuckPerms is not installed or not yet available. */
    PERMISSIONS_UNAVAILABLE,
    /** The permission backend or the audit write failed; see the detail and the log. */
    ERROR;

    public boolean successful() {
        return this == SUCCESS;
    }
}
