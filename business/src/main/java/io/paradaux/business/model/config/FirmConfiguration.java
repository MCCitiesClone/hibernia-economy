package io.paradaux.business.model.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.business.Business;

/**
 * General firm settings.
 *
 * <p>Config location: {@code config.yml} under the {@code firm:} key.
 */
@Singleton
public class FirmConfiguration {

    private static final int DEFAULT_OWNED_FIRM_LIMIT = 3;
    private static final int DEFAULT_CREATE_COOLDOWN_SECONDS = 300;

    private final Business plugin;

    // Mutable so {@link #reload()} can refresh them from disk at runtime — this is
    // a Guice singleton, so services holding a reference see the new values.
    private int ownedFirmLimit;
    private int createCooldownSeconds;

    @Inject
    public FirmConfiguration(Business plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Re-reads the values from the (already-reloaded) {@code config.yml}. Callers
     * must run {@link Business#reloadConfig()} first so {@code getConfig()} is fresh.
     */
    public void reload() {
        load();
    }

    private void load() {
        // Max firms a single player may own (be proprietor of). 0 or below = unlimited.
        this.ownedFirmLimit = plugin.getConfig().getInt("firm.owned-limit", DEFAULT_OWNED_FIRM_LIMIT);
        plugin.getLogger().info(ownedFirmLimit > 0
                ? "Firm ownership limit: " + ownedFirmLimit + " per player"
                : "Firm ownership limit: unlimited");

        // Minimum seconds between a player creating firms. Keyed on creation time
        // (not active count) so rapid create/disband cycling is throttled too.
        // 0 or below = no cooldown.
        this.createCooldownSeconds = plugin.getConfig().getInt("firm.create-cooldown-seconds", DEFAULT_CREATE_COOLDOWN_SECONDS);
        plugin.getLogger().info(createCooldownSeconds > 0
                ? "Firm creation cooldown: " + createCooldownSeconds + "s per player"
                : "Firm creation cooldown: disabled");
    }

    /** Maximum number of firms a player may own; {@code <= 0} means unlimited. */
    public int getOwnedFirmLimit() {
        return ownedFirmLimit;
    }

    /** Whether a finite ownership limit is enforced. */
    public boolean hasOwnedFirmLimit() {
        return ownedFirmLimit > 0;
    }

    /** Minimum seconds between firm creations per player; {@code <= 0} means no cooldown. */
    public int getCreateCooldownSeconds() {
        return createCooldownSeconds;
    }

    /** Whether a creation cooldown is enforced. */
    public boolean hasCreateCooldown() {
        return createCooldownSeconds > 0;
    }
}
