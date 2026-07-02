package io.paradaux.chestshop.integration;

import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.context.protection.BuildPermissionContext;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * WorldGuard region gating for shop creation. Constructed by {@code Dependencies} only
 * when WorldGuard is hooked and {@code WORLDGUARD_INTEGRATION} is on, and registered with
 * {@link io.paradaux.chestshop.services.ProtectionService} as a method reference (no
 * longer a Bukkit {@code Listener}). The former {@code ignoreCancelled=true} "skip once
 * disallowed" semantics are applied by the service.
 *
 * @author Acrobot
 */
public class WorldGuardBuilding {
    private WorldGuardPlugin worldGuard;
    private WorldGuardPlatform worldGuardPlatform;
    private final ChestShopConfiguration config;

    public WorldGuardBuilding(Plugin plugin, ChestShopConfiguration config) {
        this.worldGuard = (WorldGuardPlugin) plugin;
        this.worldGuardPlatform = WorldGuard.getInstance().getPlatform();
        this.config = config;
    }

    public void canBuild(BuildPermissionContext event) {
        ApplicableRegionSet regions = getApplicableRegions(event.getSign().getBlock().getLocation());

        if (regions == null) {
            event.allow(false);
        } else if (config.isWorldguardUseFlag()) {
            event.allow(regions.queryState(worldGuard.wrapPlayer(event.getPlayer()), WorldGuardFlags.ENABLE_SHOP) == StateFlag.State.ALLOW);
        } else {
            event.allow(regions.size() > 0);
        }
    }

    private ApplicableRegionSet getApplicableRegions(Location location) {
        RegionManager regionManager = worldGuardPlatform.getRegionContainer().get(BukkitAdapter.adapt(location.getWorld()));
        if (regionManager == null) {
            return null;
        }
        return regionManager.getApplicableRegions(BukkitAdapter.adapt(location).toVector().toBlockPoint());
    }
}
