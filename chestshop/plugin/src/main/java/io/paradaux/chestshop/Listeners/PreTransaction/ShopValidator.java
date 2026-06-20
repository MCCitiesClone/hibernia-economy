package io.paradaux.chestshop.Listeners.PreTransaction;

import io.paradaux.chestshop.Events.PreTransactionEvent;
import io.paradaux.chestshop.Signs.ChestShopSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import static io.paradaux.chestshop.Events.PreTransactionEvent.TransactionOutcome.INVALID_SHOP;

/**
 * @author Acrobot
 */
public class ShopValidator implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public static void onCheck(PreTransactionEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (isEmpty(event.getStock())) {
            event.setCancelled(INVALID_SHOP);
            return;
        }

        if (!ChestShopSign.isAdminShop(event.getSign()) && event.getOwnerInventory() == null) {
            event.setCancelled(INVALID_SHOP);
        }
    }

    private static <A> boolean isEmpty(A[] array) {
        for (A element : array) {
            if (element != null) {
                return false;
            }
        }

        return true;
    }
}
