package io.paradaux.chestshop.listeners.pretransaction;

import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.PreTransactionEvent;
import org.bukkit.GameMode;

import static io.paradaux.chestshop.events.PreTransactionEvent.TransactionOutcome.CREATIVE_MODE_PROTECTION;

/**
 * @author Acrobot
 */
public class CreativeModeIgnorer {

    public static void onPreTransaction(PreTransactionEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (Properties.IGNORE_CREATIVE_MODE && event.getClient().getGameMode() == GameMode.CREATIVE) {
            event.setCancelled(CREATIVE_MODE_PROTECTION);
        }
    }
}
