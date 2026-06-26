package io.paradaux.chestshop.listeners.posttransaction;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.TransactionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Thin entrypoint: hands a validated {@link TransactionEvent} to
 * {@link io.paradaux.chestshop.services.TransactionService}, which performs the
 * goods + money legs atomically. Replaces the former {@code ItemManager} (@NORMAL)
 * and {@code EconomicModule} (@HIGH) listeners — the orchestration that used to be
 * split across them now lives in the service.
 */
public class TransactionExecutor implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTransaction(TransactionEvent event) {
        ChestShop.transactions().execute(event);
    }
}
