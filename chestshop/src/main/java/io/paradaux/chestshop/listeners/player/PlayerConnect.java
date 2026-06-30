package io.paradaux.chestshop.listeners.player;

import com.google.inject.Inject;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.players.PlayerDTO;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.TransactionService;

/**
 * @author Acrobot
 */
public class PlayerConnect implements Listener {

    private final AccountService accounts;
    private final TransactionService transactions;

    @Inject
    public PlayerConnect(AccountService accounts, TransactionService transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerConnect(final PlayerJoinEvent event) {
        if (accounts.getUuidVersion() < 0) {
            accounts.setUuidVersion(event.getPlayer().getUniqueId().version());
        }

        final PlayerDTO playerDTO = new PlayerDTO(event.getPlayer());

        ChestShop.runInAsyncThread(() -> {
            if (accounts.getAccount(playerDTO.getUniqueId()) != null) {
                accounts.storeUsername(playerDTO);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Drop the player's notification-cooldown rows (owned by TransactionService since
        // the pre-transaction validators were folded into it).
        transactions.clearNotificationCooldowns(event.getPlayer().getUniqueId());
    }
}
