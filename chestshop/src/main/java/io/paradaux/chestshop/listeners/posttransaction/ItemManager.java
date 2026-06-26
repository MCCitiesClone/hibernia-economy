package io.paradaux.chestshop.listeners.posttransaction;

import io.paradaux.chestshop.utils.ImplementationAdapter;
import io.paradaux.chestshop.utils.InventoryUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.TransactionEvent;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;
import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.SELL;

/**
 * Moves the goods for a transaction.
 *
 * <p>Runs at {@link EventPriority#NORMAL} — after the LOWEST/LOW listeners that
 * may still cancel or reprice the trade ({@code AuthMe}, {@code RestrictedSign},
 * …) and <b>before</b> the money leg
 * ({@link EconomicModule}, now {@link EventPriority#HIGH}). This makes the trade
 * atomic (ADT-4): the items move first and, if the move cannot complete in full,
 * both inventories are reverted and the transaction is cancelled before any
 * money changes hands. The PreTransaction stage already validates stock and
 * space, so a shortfall here is exceptional (e.g. a corrupted {@link DoubleChest}
 * holder), but it must never leave a player charged for undelivered goods.</p>
 *
 * @author Acrobot
 */
public class ItemManager implements Listener {
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public static void shopItemRemover(TransactionEvent event) {
        if (event.getTransactionType() != BUY) {
            return;
        }

        if (!transferItems(event.getOwnerInventory(), event.getClientInventory(), event.getStock())) {
            cancelOnShortfall(event);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public static void inventoryItemRemover(TransactionEvent event) {
        if (event.getTransactionType() != SELL) {
            return;
        }

        if (!transferItems(event.getClientInventory(), event.getOwnerInventory(), event.getStock())) {
            cancelOnShortfall(event);
        }
    }

    private static void cancelOnShortfall(TransactionEvent event) {
        event.setCancelled(true);
        ChestShop.getBukkitLogger().severe(
                "Aborted a ChestShop transaction at "
                + (event.getSign() != null ? event.getSign().getLocation() : "<unknown location>")
                + ": the goods could not be transferred in full, so no money was moved and both "
                + "inventories were left untouched. This should not normally happen — the "
                + "PreTransaction checks validate stock and space beforehand.");
    }

    /**
     * Attempt to move every stack in {@code items} from {@code sourceInventory}
     * to {@code targetInventory}. Both inventories are snapshotted first; if any
     * item cannot be placed (the underlying transfer reports leftovers), both
     * inventories are restored to their pre-transfer state and {@code false} is
     * returned so the caller can cancel the transaction before money moves.
     *
     * @return {@code true} if every item moved; {@code false} — with both
     *         inventories reverted — on any shortfall.
     */
    static boolean transferItems(Inventory sourceInventory, Inventory targetInventory, ItemStack[] items) {
        ItemStack[] sourceSnapshot = cloneContents(sourceInventory);
        ItemStack[] targetSnapshot = cloneContents(targetInventory);

        int leftOver = 0;
        for (ItemStack item : items) {
            leftOver += Properties.STACK_TO_64
                    ? InventoryUtil.transfer(item, sourceInventory, targetInventory, 64)
                    : InventoryUtil.transfer(item, sourceInventory, targetInventory);
        }

        if (leftOver > 0) {
            sourceInventory.setContents(sourceSnapshot);
            targetInventory.setContents(targetSnapshot);
            return false;
        }

        update(sourceInventory.getHolder());
        update(targetInventory.getHolder());
        return true;
    }

    /**
     * Reverse a completed item move. Used by the money leg to compensate when
     * the (pre-validated, so exceptional) money transfer fails <em>after</em>
     * the goods have already moved at {@link EventPriority#NORMAL} — putting the
     * items back keeps the trade atomic. Because the forward move completed in
     * full, the stacks are present and there is room for them in the original
     * inventory, so the reversal is expected to succeed; a failure is logged.
     */
    static void reverseTransfer(TransactionEvent event) {
        boolean reversed = event.getTransactionType() == BUY
                // Forward move was owner -> client; undo client -> owner.
                ? transferItems(event.getClientInventory(), event.getOwnerInventory(), event.getStock())
                // Forward move was client -> owner; undo owner -> client.
                : transferItems(event.getOwnerInventory(), event.getClientInventory(), event.getStock());

        if (!reversed) {
            ChestShop.getBukkitLogger().severe(
                    "Failed to reverse the goods of a ChestShop transaction at "
                    + (event.getSign() != null ? event.getSign().getLocation() : "<unknown location>")
                    + " after the money leg failed. The trade may be in an inconsistent state.");
        }
    }

    private static ItemStack[] cloneContents(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private static void update(InventoryHolder holder) {
        if (holder instanceof Player) {
            ((Player) holder).updateInventory();
        } else if (holder instanceof BlockState) {
            ((BlockState) holder).update();
        } else if (holder instanceof DoubleChest) {
            update(ImplementationAdapter.getLeftSide((DoubleChest) holder, false));
            update(ImplementationAdapter.getRightSide((DoubleChest) holder, false));
        }
    }
}
