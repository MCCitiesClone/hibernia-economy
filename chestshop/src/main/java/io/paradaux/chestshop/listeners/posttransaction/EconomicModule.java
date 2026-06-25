package io.paradaux.chestshop.listeners.posttransaction;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.economy.CurrencyTransferEvent;
import io.paradaux.chestshop.events.TransactionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;

/**
 * The money leg of a transaction. Runs at {@link EventPriority#HIGH}, after the
 * goods have already been moved by {@link ItemManager} (NORMAL). This ordering
 * makes the trade atomic (ADT-4): if the goods could not be delivered in full
 * the transaction is already cancelled by the time this runs, so no money moves;
 * and if the (pre-validated, so exceptional) money transfer itself fails here,
 * the already-moved goods are reversed before the transaction is cancelled.
 *
 * @author Acrobot
 */
public class EconomicModule implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
            // The goods already moved at NORMAL priority but the money leg
            // failed (or no economy handled it), so put the goods back to keep
            // the trade atomic, then cancel.
            ItemManager.reverseTransfer(event);
            event.setCancelled(true);
        }
    }
}
