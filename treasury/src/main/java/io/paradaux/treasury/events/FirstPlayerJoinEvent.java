package io.paradaux.treasury.events;

import com.google.inject.Inject;
import io.paradaux.treasury.Treasury;
import io.paradaux.treasury.services.LedgerService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class FirstPlayerJoinEvent implements Listener {

    private final Treasury plugin;
    private final LedgerService ledgerService;

    @Inject
    public FirstPlayerJoinEvent(Treasury plugin, LedgerService ledgerService) {
        this.plugin = plugin;
        this.ledgerService = ledgerService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Skip Citizens / fake-player NPCs. They carry player UUIDs and fire join
        // events, but they aren't real players — funding them mints starting balances
        // into phantom accounts and pollutes the ledger + /baltop.
        if (player.hasMetadata("NPC")) {
            return;
        }
        UUID uuid = player.getUniqueId();
        // Creates PERSONAL account if missing and funds it with the configured starting balance.
        // Dispatched async to avoid blocking the main thread with DB operations on join.
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> ledgerService.resolveOrCreatePersonal(uuid));
    }
}
