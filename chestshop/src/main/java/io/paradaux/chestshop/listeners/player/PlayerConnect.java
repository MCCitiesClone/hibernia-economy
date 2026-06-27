package io.paradaux.chestshop.listeners.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.listeners.pretransaction.ErrorMessageSender;
import io.paradaux.chestshop.players.PlayerDTO;

/**
 * @author Acrobot
 */
public class PlayerConnect implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public static void onPlayerConnect(final PlayerJoinEvent event) {
        if (ChestShop.accounts().getUuidVersion() < 0) {
            ChestShop.accounts().setUuidVersion(event.getPlayer().getUniqueId().version());
        }

        final PlayerDTO playerDTO = new PlayerDTO(event.getPlayer());

        ChestShop.runInAsyncThread(() -> {
            if (ChestShop.accounts().getAccount(playerDTO.getUniqueId()) != null) {
                ChestShop.accounts().storeUsername(playerDTO);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public static void onPlayerQuit(final PlayerQuitEvent event) {
        // Drop the player's notification-cooldown rows. This was ErrorMessageSender's own
        // PlayerQuitEvent handler, orphaned when the pre-transaction listeners were
        // collapsed into TransactionService; re-homed here so the table still clears.
        ErrorMessageSender.onQuit(event);
    }
}
