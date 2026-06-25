package io.paradaux.treasury.events;

import com.google.inject.Inject;
import io.paradaux.treasury.Treasury;
import io.paradaux.treasury.services.PlayerDirectoryService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Maintains the unified player directory ({@code economy_players}) on every login:
 * records the player's current name and last-login epoch (PAR-35). Unlike the tax
 * listener this is always active — the directory must stay complete regardless of
 * whether balance tax is enabled, since it's the name → UUID resolver other
 * commands rely on. The DB write runs async so it never blocks the main thread.
 */
public class EconomyPlayerLoginListener implements Listener {

    private final Treasury plugin;
    private final PlayerDirectoryService playerDirectory;

    @Inject
    public EconomyPlayerLoginListener(Treasury plugin, PlayerDirectoryService playerDirectory) {
        this.plugin = plugin;
        this.playerDirectory = playerDirectory;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        long loginEpochSecs = Instant.now().getEpochSecond();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                playerDirectory.recordLogin(playerUuid, name, loginEpochSecs);
            } catch (RuntimeException e) {
                // Best-effort directory upkeep: never let a write failure surface as an
                // uncaught async scheduler error. Log so a persistent problem is visible.
                plugin.getLogger().warning("Failed to record player directory entry for "
                        + name + " (" + playerUuid + "): " + e);
            }
        });
    }
}
