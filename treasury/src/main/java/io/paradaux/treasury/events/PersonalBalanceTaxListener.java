package io.paradaux.treasury.events;

import com.google.inject.Inject;
import io.paradaux.treasury.Treasury;
import io.paradaux.treasury.model.config.BalanceTaxConfiguration;
import io.paradaux.treasury.services.BalanceTaxService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Triggers prorated personal balance tax collection on every player login.
 *
 * <p>The tax is computed and collected asynchronously so that DB operations
 * do not block the main thread. The login timestamp is captured synchronously
 * on the event (before any async delay) so the proration window is precise.
 *
 * <p>This listener no-ops immediately when
 * {@link BalanceTaxConfiguration#isEnabled()} is {@code false}.
 */
public class PersonalBalanceTaxListener implements Listener {

    private final Treasury plugin;
    private final BalanceTaxService balanceTaxService;
    private final BalanceTaxConfiguration config;

    @Inject
    public PersonalBalanceTaxListener(Treasury plugin,
                                      BalanceTaxService balanceTaxService,
                                      BalanceTaxConfiguration config) {
        this.plugin           = plugin;
        this.balanceTaxService = balanceTaxService;
        this.config           = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        UUID playerUuid    = event.getPlayer().getUniqueId();
        long loginEpochSecs = Instant.now().getEpochSecond();

        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> balanceTaxService.processLogin(playerUuid, loginEpochSecs));
    }
}
