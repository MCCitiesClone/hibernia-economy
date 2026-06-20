package io.paradaux.treasuryapi.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure set diff for membership reconciliation: given the desired members (from
 * LuckPerms) and the current synced members, compute who to add and remove.
 * Kept free of Bukkit/LuckPerms so it is unit-testable in isolation.
 */
public final class ReconciliationDiff {

    private ReconciliationDiff() {}

    public record Result(Set<UUID> toAdd, Set<UUID> toRemove) {}

    public static Result of(Set<UUID> desired, Set<UUID> current) {
        Set<UUID> toAdd = new HashSet<>(desired);
        toAdd.removeAll(current);
        Set<UUID> toRemove = new HashSet<>(current);
        toRemove.removeAll(desired);
        return new Result(toAdd, toRemove);
    }
}
