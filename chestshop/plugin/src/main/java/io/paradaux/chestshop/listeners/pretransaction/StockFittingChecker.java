package io.paradaux.chestshop.listeners.pretransaction;

import io.paradaux.chestshop.breeze.utils.InventoryUtil;
import io.paradaux.chestshop.database.Item;
import io.paradaux.chestshop.events.PreTransactionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import static io.paradaux.chestshop.events.PreTransactionEvent.TransactionOutcome.NOT_ENOUGH_SPACE_IN_CHEST;
import static io.paradaux.chestshop.events.PreTransactionEvent.TransactionOutcome.NOT_ENOUGH_SPACE_IN_INVENTORY;
import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;
import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.SELL;

/**
 * @author Acrobot
 */
public class StockFittingChecker implements Listener {
    @EventHandler
    public static void onSellCheck(PreTransactionEvent event) {
        if (event.isCancelled() || event.getTransactionType() != SELL) {
            return;
        }

        Inventory shopInventory = event.getOwnerInventory();
        ItemStack[] stock = event.getStock();

        if (!InventoryUtil.fits(stock, shopInventory)) {
            event.setCancelled(NOT_ENOUGH_SPACE_IN_CHEST);
        }
    }

    @EventHandler
    public static void onBuyCheck(PreTransactionEvent event) {
        if (event.isCancelled() || event.getTransactionType() != BUY) {
            return;
        }

        Inventory clientInventory = event.getClientInventory();
        ItemStack[] stock = event.getStock();

        if (!InventoryUtil.fits(stock, clientInventory)) {
            event.setCancelled(NOT_ENOUGH_SPACE_IN_INVENTORY);
        }
    }
}
