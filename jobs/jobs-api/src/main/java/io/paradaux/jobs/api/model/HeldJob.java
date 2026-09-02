package io.paradaux.jobs.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * A job a player currently holds, as resolved from LuckPerms.
 *
 * <p>{@code direct} distinguishes the two ways a player can hold a job group, and
 * the difference is not cosmetic: only a <em>direct</em> parent node can be removed
 * by {@code /fire} or {@code /quit}. A job inherited from a parent rank shows in
 * {@code /jobs} — hiding it would misrepresent the player's real authority — but
 * attempting to remove it yields {@link io.paradaux.jobs.api.Outcome#INHERITED_NOT_DIRECT},
 * because the fix belongs in the rank, not in this plugin.</p>
 */
public record HeldJob(@NotNull JobId id,
                      @NotNull String displayName,
                      @NotNull String typeKey,
                      @NotNull String typeDisplayName,
                      boolean direct) {
}
