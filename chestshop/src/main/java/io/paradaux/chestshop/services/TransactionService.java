package io.paradaux.chestshop.services;

import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.TransactionEvent;
import io.paradaux.chestshop.events.economy.CurrencyTransferEvent;
import io.paradaux.chestshop.utils.ImplementationAdapter;
import io.paradaux.chestshop.utils.InventoryUtil;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;

/**
 * Owns the atomic core of a shop trade — the goods leg and the money leg — in one
 * place, with an explicit ordering and rollback, instead of spreading it across two
 * priority-ordered listeners ({@code ItemManager} @NORMAL + {@code EconomicModule}
 * @HIGH) that coordinated through a shared mutable {@link TransactionEvent}. This is
 * the atomicity guarantee ADT-4 was about, now readable and unit-testable as a
 * single method.
 *
 * <p>The money itself still flows through {@link CurrencyTransferEvent} — that event
 * is a genuine integration point (Treasury settles it, the tax + message modules
 * react to it), so this service fires it rather than swallowing it. Downstream
 * post-transaction reactions (empty-shop cleanup, messages, logging) remain their
 * own listeners for now; collapsing those is a later phase.
 */
@Singleton
public class TransactionService {

    /**
     * Run a validated transaction to completion: move the goods, then settle the
     * money — reversing the goods if the (pre-validated, so exceptional) money leg
     * fails, so the trade is all-or-nothing.
     */
    public void execute(TransactionEvent event) {
        boolean buy = event.getTransactionType() == BUY;

        // Goods leg (was ItemManager @NORMAL): BUY moves owner → client, SELL the
        // reverse. transferItems reverts both inventories on any shortfall.
        Inventory from = buy ? event.getOwnerInventory() : event.getClientInventory();
        Inventory to = buy ? event.getClientInventory() : event.getOwnerInventory();
        if (!transferItems(from, to, event.getStock())) {
            cancelOnShortfall(event);
            return;
        }

        // Money leg (was EconomicModule @HIGH): settle via CurrencyTransferEvent.
        CurrencyTransferEvent currency = new CurrencyTransferEvent(
                event.getExactPrice(),
                event.getClient(),
                event.getOwnerAccount().getUuid(),
                buy ? CurrencyTransferEvent.Direction.PARTNER : CurrencyTransferEvent.Direction.INITIATOR,
                event);
        ChestShop.callEvent(currency);

        if (!currency.wasHandled()) {
            // The goods already moved but nothing settled the money — put the goods
            // back and cancel, keeping the trade atomic.
            reverseTransfer(event);
            event.setCancelled(true);
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
     * Move every stack from {@code source} to {@code target}. Both inventories are
     * snapshotted first; on any shortfall both are restored and {@code false} is
     * returned so the caller can cancel before money moves.
     */
    static boolean transferItems(Inventory source, Inventory target, ItemStack[] items) {
        ItemStack[] sourceSnapshot = cloneContents(source);
        ItemStack[] targetSnapshot = cloneContents(target);

        int leftOver = 0;
        for (ItemStack item : items) {
            leftOver += Properties.STACK_TO_64
                    ? InventoryUtil.transfer(item, source, target, 64)
                    : InventoryUtil.transfer(item, source, target);
        }

        if (leftOver > 0) {
            source.setContents(sourceSnapshot);
            target.setContents(targetSnapshot);
            return false;
        }

        update(source.getHolder());
        update(target.getHolder());
        return true;
    }

    /** Reverse a completed goods move after a failed money leg, keeping the trade atomic. */
    static void reverseTransfer(TransactionEvent event) {
        boolean reversed = event.getTransactionType() == BUY
                ? transferItems(event.getClientInventory(), event.getOwnerInventory(), event.getStock())
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
        if (holder instanceof Player player) {
            player.updateInventory();
        } else if (holder instanceof BlockState blockState) {
            blockState.update();
        } else if (holder instanceof DoubleChest doubleChest) {
            update(ImplementationAdapter.getLeftSide(doubleChest, false));
            update(ImplementationAdapter.getRightSide(doubleChest, false));
        }
    }
}
