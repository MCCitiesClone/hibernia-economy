package io.paradaux.chestshop.plugins;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.PreTransactionEvent;
import io.paradaux.chestshop.events.PreTransactionEvent.TransactionOutcome;

public class AuthMe implements Listener {

    private AuthMeApi authmeApi = AuthMeApi.getInstance();

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPreTransaction(PreTransactionEvent event) {
        if (event.getClient() == null) {
            return;
        }
        Player player = event.getClient();

        if (!Properties.AUTHME_HOOK) {
            return;
        }

        if (authmeApi.isUnrestricted(player)) {
            return;
        }

        if (!authmeApi.isRegistered(player.getName()) && Properties.AUTHME_ALLOW_UNREGISTERED) {
            return;
        }

        if (authmeApi.isAuthenticated(player)) {
            return;
        }

        event.setCancelled(TransactionOutcome.CLIENT_DOES_NOT_HAVE_PERMISSION);
    }
}