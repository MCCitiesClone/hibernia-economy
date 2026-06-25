package io.paradaux.chestshop.listeners.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.uuids.NameManager;
import io.paradaux.chestshop.uuids.PlayerDTO;

/**
 * @author Acrobot
 */
public class PlayerConnect implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public static void onPlayerConnect(final PlayerJoinEvent event) {
        if (NameManager.getUuidVersion() < 0) {
            NameManager.setUuidVersion(event.getPlayer().getUniqueId().version());
        }

        final PlayerDTO playerDTO = new PlayerDTO(event.getPlayer());

        ChestShop.runInAsyncThread(() -> {
            if (NameManager.getAccount(playerDTO.getUniqueId()) != null) {
                NameManager.storeUsername(playerDTO);
            }
        });
    }
}
