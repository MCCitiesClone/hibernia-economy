package io.paradaux.chestshop.listeners.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import io.paradaux.chestshop.ChestShop;
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
}
