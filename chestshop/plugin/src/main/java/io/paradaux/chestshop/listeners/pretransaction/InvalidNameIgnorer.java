package io.paradaux.chestshop.listeners.pretransaction;

import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.PreTransactionEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.regex.Pattern;

public class InvalidNameIgnorer implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public static void onPreTransaction(PreTransactionEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Pattern USERNAME_PATTERN = Pattern.compile(Properties.VALID_PLAYERNAME_REGEXP);
        String name = event.getClient().getName();
        if (ChestShopSign.isAdminShop(name) || !USERNAME_PATTERN.matcher(name).matches()) {
            event.setCancelled(PreTransactionEvent.TransactionOutcome.INVALID_CLIENT_NAME);
        }
    }
}
