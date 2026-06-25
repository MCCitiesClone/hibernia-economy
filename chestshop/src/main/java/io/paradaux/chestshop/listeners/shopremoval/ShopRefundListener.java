package io.paradaux.chestshop.listeners.shopremoval;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Messages;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.economy.Economy;
import io.paradaux.chestshop.events.AccountQueryEvent;
import io.paradaux.chestshop.events.economy.CurrencyAddEvent;
import io.paradaux.chestshop.events.economy.CurrencySubtractEvent;
import io.paradaux.chestshop.events.ShopDestroyedEvent;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.players.NameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.math.BigDecimal;

import static io.paradaux.chestshop.Permission.NOFEE;
import static io.paradaux.chestshop.signs.ChestShopSign.AUTOFILL_CODE;

/**
 * @author Acrobot
 */
public class ShopRefundListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public static void onShopDestroy(ShopDestroyedEvent event) {
        BigDecimal refundPrice = Properties.SHOP_REFUND_PRICE;

        if (event.getDestroyer() == null || Permission.has(event.getDestroyer(), NOFEE) || refundPrice.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        if (ChatColor.stripColor(ChestShopSign.getItem(event.getSign())).equals(AUTOFILL_CODE)) {
            return;
        }

        AccountQueryEvent accountQueryEvent = new AccountQueryEvent(ChestShopSign.getOwner(event.getSign()));
        Bukkit.getPluginManager().callEvent(accountQueryEvent);
        Account account = accountQueryEvent.getAccount();
        if (account == null) {
            return;
        }

        CurrencyAddEvent currencyEvent = new CurrencyAddEvent(refundPrice, account.getUuid(), event.getSign().getWorld());
        ChestShop.callEvent(currencyEvent);

        if (NameManager.getServerEconomyAccount() != null) {
            CurrencySubtractEvent currencySubtractEvent = new CurrencySubtractEvent(
                    refundPrice,
                    NameManager.getServerEconomyAccount().getUuid(),
                    event.getSign().getWorld());
            ChestShop.callEvent(currencySubtractEvent);
        }

        Messages.SHOP_REFUNDED.sendWithPrefix(event.getDestroyer(), "amount", Economy.formatBalance(refundPrice));
    }
}
