package io.paradaux.chestshop.integration.worldguard;
import io.paradaux.chestshop.integration.Integration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.services.ProtectionService;
import org.bukkit.plugin.Plugin;

/**
 * WorldGuard soft-dependency: region/built-in protection ({@code allow-shop} flag) and, when
 * enabled, region-gated shop creation. Both sides are config-gated — {@code WORLDGUARD_USE}
 * for protection, {@code WORLDGUARD_INTEGRATION} for build gating.
 *
 * <p>The custom {@code allow-shop} flag must be registered in {@code onLoad} (before WorldGuard
 * enables), which is pre-Guice — that stays a direct {@code WorldGuardFlags} call in
 * {@code ChestShop.onLoad}; this integration only wires the runtime protection providers.
 */
@Singleton
public class WorldGuardIntegration implements Integration {

    private final ProtectionService protection;
    private final ChestShopConfiguration config;

    @Inject
    public WorldGuardIntegration(ProtectionService protection, ChestShopConfiguration config) {
        this.protection = protection;
        this.config = config;
    }

    @Override
    public String pluginName() {
        return "WorldGuard";
    }

    @Override
    public boolean hook(Plugin plugin) {
        if (!config.isWorldguardUseProtection() && !config.isWorldguardIntegration()) {
            return false;
        }
        if (config.isWorldguardUseProtection()) {
            protection.setWorldGuardProtection(new WorldGuardProtection(plugin)::onProtectionCheck);
        }
        if (config.isWorldguardIntegration()) {
            protection.setWorldGuardBuilding(new WorldGuardBuilding(plugin, config)::canBuild);
        }
        return true;
    }
}
