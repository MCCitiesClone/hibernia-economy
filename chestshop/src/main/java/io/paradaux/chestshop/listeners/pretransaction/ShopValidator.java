package io.paradaux.chestshop.listeners.pretransaction;

import io.paradaux.chestshop.events.PreTransactionEvent;
import io.paradaux.chestshop.signs.ChestShopSign;

import static io.paradaux.chestshop.events.PreTransactionEvent.TransactionOutcome.INVALID_SHOP;

/**
 * @author Acrobot
 */
public class ShopValidator {
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
