package io.paradaux.chestshop.integration.griefprevention;
import io.paradaux.chestshop.integration.Integration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.services.ProtectionService;
import org.bukkit.plugin.Plugin;

/**
 * GriefPrevention soft-dependency: gates shop creation on the player's claim permissions,
 * when {@code GRIEFPREVENTION_INTEGRATION} is on.
 */
@Singleton
public class GriefPreventionIntegration implements Integration {

    private final ProtectionService protection;
    private final ChestShopConfiguration config;

    @Inject
    public GriefPreventionIntegration(ProtectionService protection, ChestShopConfiguration config) {
        this.protection = protection;
        this.config = config;
    }

    @Override
    public String pluginName() {
        return "GriefPrevention";
    }

    @Override
    public boolean hook(Plugin plugin) {
        if (!config.isGriefpreventionIntegration()) {
            return false;
        }
        protection.setGriefPreventionBuilding(new GriefPreventionBuilding(plugin)::canBuild);
        return true;
    }
}
