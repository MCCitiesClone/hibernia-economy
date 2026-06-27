package io.paradaux.chestshop.listeners.pretransaction;

import io.paradaux.chestshop.events.PreTransactionEvent;
import io.paradaux.chestshop.events.TransactionEvent;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

import static io.paradaux.chestshop.events.PreTransactionEvent.TransactionOutcome.CLIENT_DOES_NOT_HAVE_PERMISSION;
import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;

/**
 * @author Acrobot
 */
public class PermissionChecker {
    public static void onPermissionCheck(PreTransactionEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player client = event.getClient();
        TransactionEvent.TransactionType transactionType = event.getTransactionType();

        String itemLine = ChestShopSign.getItem(event.getSign());
        if (itemLine.contains("#") && Permission.hasPermissionSetFalse(client, (transactionType == BUY ? Permission.BUY_ID : Permission.SELL_ID) + itemLine)) {
            event.setCancelled(CLIENT_DOES_NOT_HAVE_PERMISSION);
            return;
        }

        for (ItemStack stock : event.getStock()) {
            String matID = stock.getType().toString().toLowerCase(Locale.ROOT);

            boolean hasPerm;

            if (transactionType == BUY) {
                hasPerm = Permission.has(client, Permission.BUY) && !Permission.hasPermissionSetFalse(client, Permission.BUY_ID + matID)
                        || Permission.has(client, Permission.BUY_ID + matID);
            } else {
                hasPerm = Permission.has(client, Permission.SELL) && !Permission.hasPermissionSetFalse(client, Permission.SELL_ID + matID)
                        || Permission.has(client, Permission.SELL_ID + matID);
            }

            if (!hasPerm) {
                event.setCancelled(CLIENT_DOES_NOT_HAVE_PERMISSION);
                return;
            }
        }
    }
}
