package io.paradaux.chestshop.utils;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Thin, named accessor for Paper's snapshot/non-snapshot inventory + block-state reads.
 * Formerly reflectively probed for the {@code getHolder(boolean)}/{@code getState(boolean)}
 * overloads and fell back to the snapshot-only API on servers lacking them; ChestShop now
 * targets the Paper 1.21.11 API where those overloads always exist, so it calls them directly
 * (the reflection + the {@code utils/compat/} indirection were removed — PAR-297). Kept as a
 * single named seam for "pass {@code useSnapshot=false} to read the live container".
 */
public final class ImplementationAdapter {

    private ImplementationAdapter() {
    }

    /**
     * Get the inventory's holder.
     * @param inventory     The inventory
     * @param useSnapshot   Whether the holder should be a snapshot
     * @return The inventory's holder
     */
    public static InventoryHolder getHolder(Inventory inventory, boolean useSnapshot) {
        return inventory.getHolder(useSnapshot);
    }

    /**
     * Get a DoubleChest's left side
     * @param doubleChest   The DoubleChest
     * @param useSnapshot   Whether the holder should be a snapshot
     * @return The left side's holder
     */
    public static InventoryHolder getLeftSide(DoubleChest doubleChest, boolean useSnapshot) {
        return doubleChest.getLeftSide(useSnapshot);
    }

    /**
     * Get a DoubleChest's right side
     * @param doubleChest   The DoubleChest
     * @param useSnapshot   Whether the holder should be a snapshot
     * @return The right side's holder
     */
    public static InventoryHolder getRightSide(DoubleChest doubleChest, boolean useSnapshot) {
        return doubleChest.getRightSide(useSnapshot);
    }

    /**
     * Get a block state
     * @param block         The block to get the state from
     * @param useSnapshot   Whether the state should be a snapshot
     * @return The block's state
     */
    public static BlockState getState(Block block, boolean useSnapshot) {
        return block.getState(useSnapshot);
    }
}
