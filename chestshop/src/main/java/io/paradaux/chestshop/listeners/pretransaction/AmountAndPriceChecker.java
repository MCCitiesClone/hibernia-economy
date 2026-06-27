package io.paradaux.chestshop.listeners.pretransaction;

import io.paradaux.chestshop.utils.InventoryUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.PreTransactionEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

import static io.paradaux.chestshop.events.PreTransactionEvent.TransactionOutcome.*;
import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;
import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.SELL;

/**
 * @author Acrobot
 */
public class AmountAndPriceChecker {

    public static void onBuyItemCheck(PreTransactionEvent event) {
        if (event.getTransactionType() != BUY) {
            return;
        }

        ItemStack[] stock = event.getStock();
        Inventory ownerInventory = event.getOwnerInventory();

        if (!ChestShop.economy().hasFunds(event.getClient().getUniqueId(), event.getExactPrice())) {
            event.setCancelled(CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY);
            return;
        }

        if (!InventoryUtil.hasItems(stock, ownerInventory)) {
            event.setCancelled(NOT_ENOUGH_STOCK_IN_CHEST);
        }
    }

    public static void onSellItemCheck(PreTransactionEvent event) {
        if (event.getTransactionType() != SELL) {
            return;
        }

        ItemStack[] stock = event.getStock();
        Inventory clientInventory = event.getClientInventory();

        if (!ChestShop.economy().hasFunds(event.getOwnerAccount().getUuid(), event.getExactPrice())) {
            event.setCancelled(SHOP_DOES_NOT_HAVE_ENOUGH_MONEY);
            return;
        }

        if (!InventoryUtil.hasItems(stock, clientInventory)) {
            event.setCancelled(NOT_ENOUGH_STOCK_IN_INVENTORY);
        }
    }
}
