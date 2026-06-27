package io.paradaux.chestshop.listeners.pretransaction;

import io.paradaux.chestshop.events.PreTransactionEvent;
import io.paradaux.chestshop.events.TransactionEvent;

import java.math.BigDecimal;

import static io.paradaux.chestshop.utils.PriceUtil.NO_PRICE;
import static io.paradaux.chestshop.events.PreTransactionEvent.TransactionOutcome.SHOP_DOES_NOT_BUY_THIS_ITEM;
import static io.paradaux.chestshop.events.PreTransactionEvent.TransactionOutcome.SHOP_DOES_NOT_SELL_THIS_ITEM;
import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;

/**
 * @author Acrobot
 */
public class PriceValidator {
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
