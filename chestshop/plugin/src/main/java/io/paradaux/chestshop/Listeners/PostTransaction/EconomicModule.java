package io.paradaux.chestshop.Listeners.PostTransaction;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Events.Economy.CurrencyTransferEvent;
import io.paradaux.chestshop.Events.TransactionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import static io.paradaux.chestshop.Events.TransactionEvent.TransactionType.BUY;

/**
 * @author Acrobot
 */
public class EconomicModule implements Listener {

    @EventHandler(ignoreCancelled = true)
    public static void onBuyTransaction(TransactionEvent event) {
        CurrencyTransferEvent currencyTransferEvent = new CurrencyTransferEvent(
                event.getExactPrice(),
                event.getClient(),
                event.getOwnerAccount().getUuid(),
                event.getTransactionType() == BUY ? CurrencyTransferEvent.Direction.PARTNER : CurrencyTransferEvent.Direction.INITIATOR,
                event
        );
        ChestShop.callEvent(currencyTransferEvent);
        if (!currencyTransferEvent.wasHandled()) {
            event.setCancelled(true);
        }
    }
}
