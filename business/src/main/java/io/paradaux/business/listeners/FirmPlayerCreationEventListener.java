package io.paradaux.business.listeners;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.business.services.FirmPlayerService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

@Singleton
public class FirmPlayerCreationEventListener implements Listener {

    private final FirmPlayerService players;

    @Inject
    public FirmPlayerCreationEventListener(FirmPlayerService players) {
        this.players = players;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        // For test server


        // Already async, so we can safely call DB here without rescheduling.
        try {
            UUID uuid = event.getUniqueId();
            String name = event.getName();
            players.touch(uuid, name);
        } catch (Exception e) {
            // Don’t fail login for a cache miss; just log it.
            Bukkit.getLogger().warning("[Business] Failed to upsert firm_players: " + e.getMessage());
        }
    }
}
