package io.paradaux.chestshop.integration;

import io.paradaux.chestshop.model.BuildPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.plugin.Plugin;

/**
 * GriefPrevention claim gating for shop creation. Constructed by {@code Dependencies} only
 * when GriefPrevention is hooked and {@code GRIEFPREVENTION_INTEGRATION} is on, and
 * registered with {@link io.paradaux.chestshop.services.ProtectionService} as a method
 * reference (no longer a Bukkit {@code Listener}).
 *
 * @author Acrobot
 */
public class GriefPrevenentionBuilding {
    private GriefPrevention griefPrevention;

    public GriefPrevenentionBuilding(Plugin plugin) {
        this.griefPrevention = (GriefPrevention) plugin;
    }

    public void canBuild(BuildPermission event) {
        event.allow(griefPrevention.dataStore.getClaimAt(event.getSign(), false, null) != null);
    }
}
