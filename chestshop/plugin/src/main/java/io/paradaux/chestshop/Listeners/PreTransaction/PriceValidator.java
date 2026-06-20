package io.paradaux.chestshop.Listeners.PreTransaction;

import io.paradaux.chestshop.Events.PreTransactionEvent;
import io.paradaux.chestshop.Events.TransactionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.math.BigDecimal;

import static io.paradaux.chestshop.breeze.Utils.PriceUtil.NO_PRICE;
import static io.paradaux.chestshop.Events.PreTransactionEvent.TransactionOutcome.SHOP_DOES_NOT_BUY_THIS_ITEM;
import static io.paradaux.chestshop.Events.PreTransactionEvent.TransactionOutcome.SHOP_DOES_NOT_SELL_THIS_ITEM;
import static io.paradaux.chestshop.Events.TransactionEvent.TransactionType.BUY;

/**
 * @author Acrobot
 */
public class PriceValidator implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public static void onPriceCheck(PreTransactionEvent event) {
        if (event.isCancelled()) {
            return;
        }

        TransactionEvent.TransactionType transactionType = event.getTransactionType();
        BigDecimal price = event.getExactPrice();

        if (price.equals(NO_PRICE)) {
            if (transactionType == BUY) {
                event.setCancelled(SHOP_DOES_NOT_SELL_THIS_ITEM);
            } else {
                event.setCancelled(SHOP_DOES_NOT_BUY_THIS_ITEM);
            }
        }
    }
}
