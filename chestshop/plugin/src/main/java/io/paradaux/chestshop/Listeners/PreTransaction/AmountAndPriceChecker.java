package io.paradaux.chestshop.Listeners.PreTransaction;

import io.paradaux.chestshop.breeze.Utils.InventoryUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Events.Economy.CurrencyCheckEvent;
import io.paradaux.chestshop.Events.PreTransactionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

import static io.paradaux.chestshop.Events.PreTransactionEvent.TransactionOutcome.*;
import static io.paradaux.chestshop.Events.TransactionEvent.TransactionType.BUY;
import static io.paradaux.chestshop.Events.TransactionEvent.TransactionType.SELL;

/**
 * @author Acrobot
 */
public class AmountAndPriceChecker implements Listener {

    @EventHandler(ignoreCancelled = true)
    public static void onBuyItemCheck(PreTransactionEvent event) {
        if (event.getTransactionType() != BUY) {
            return;
        }

        ItemStack[] stock = event.getStock();
        Inventory ownerInventory = event.getOwnerInventory();

        CurrencyCheckEvent currencyCheckEvent = new CurrencyCheckEvent(event.getExactPrice(), event.getClient());
        ChestShop.callEvent(currencyCheckEvent);

        if (!currencyCheckEvent.hasEnough()) {
            event.setCancelled(CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY);
            return;
        }

        if (!InventoryUtil.hasItems(stock, ownerInventory)) {
            event.setCancelled(NOT_ENOUGH_STOCK_IN_CHEST);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public static void onSellItemCheck(PreTransactionEvent event) {
        if (event.getTransactionType() != SELL) {
            return;
        }

        ItemStack[] stock = event.getStock();
        Inventory clientInventory = event.getClientInventory();

        CurrencyCheckEvent currencyCheckEvent = new CurrencyCheckEvent(event.getExactPrice(),
                                                        event.getOwnerAccount().getUuid(),
                                                        event.getSign().getWorld());
        ChestShop.callEvent(currencyCheckEvent);

        if (!currencyCheckEvent.hasEnough()) {
            event.setCancelled(SHOP_DOES_NOT_HAVE_ENOUGH_MONEY);
            return;
        }

        if (!InventoryUtil.hasItems(stock, clientInventory)) {
            event.setCancelled(NOT_ENOUGH_STOCK_IN_INVENTORY);
        }
    }
}
