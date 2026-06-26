package io.paradaux.chestshop.services;

import com.google.inject.Singleton;
import io.paradaux.chestshop.events.protection.BuildPermissionEvent;
import io.paradaux.chestshop.events.protection.ProtectionCheckEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Owns shop/chest protection checks, replacing the {@code ProtectionCheckEvent} and
 * {@code BuildPermissionEvent} bus with direct, ordered calls. The vanilla shop-member
 * check ({@link io.paradaux.chestshop.plugins.ChestShop#onProtectionCheck}) always runs;
 * the optional WorldGuard/GriefPrevention integrations run after it only when those
 * plugins are hooked and their config flags are on — {@code Dependencies} registers them
 * here as method references (so this service never names the {@code com.sk89q}/
 * {@code me.ryanhamshire} classes, keeping them off the call path when the plugin is
 * absent, exactly as the old conditional listener registration did).
 *
 * <p>{@code ProtectBlockEvent} had no handlers (the LWC/Lockette integrations were
 * removed), so block protection is unconditionally unavailable — {@link io.paradaux.chestshop.Security#protect}
 * always returns {@code false} and the event was dropped entirely.
 */
@Singleton
public class ProtectionService {

    @Nullable private volatile Consumer<ProtectionCheckEvent> worldGuardProtection;
    @Nullable private volatile Consumer<BuildPermissionEvent> worldGuardBuilding;
    @Nullable private volatile Consumer<BuildPermissionEvent> griefPreventionBuilding;

    /** Hook WorldGuard region/built-in protection into access checks (WORLDGUARD_USE_PROTECTION). */
    public void setWorldGuardProtection(Consumer<ProtectionCheckEvent> check) {
        this.worldGuardProtection = check;
    }

    /** Hook WorldGuard region gating into shop-creation build checks (WORLDGUARD_INTEGRATION). */
    public void setWorldGuardBuilding(Consumer<BuildPermissionEvent> check) {
        this.worldGuardBuilding = check;
    }

    /** Hook GriefPrevention claim gating into shop-creation build checks (GRIEFPREVENTION_INTEGRATION). */
    public void setGriefPreventionBuilding(Consumer<BuildPermissionEvent> check) {
        this.griefPreventionBuilding = check;
    }

    /** Whether {@code player} may access {@code block} (vanilla shop membership + WorldGuard). */
    public boolean canAccess(Block block, Player player, boolean ignoreBuiltInProtection) {
        return runProtectionCheck(new ProtectionCheckEvent(block, player, ignoreBuiltInProtection));
    }

    /** Whether {@code player} may view {@code block} (as {@link #canAccess} but without the manage check). */
    public boolean canView(Block block, Player player, boolean ignoreBuiltInProtection) {
        return runProtectionCheck(new ProtectionCheckEvent(block, player, ignoreBuiltInProtection, false));
    }

    private boolean runProtectionCheck(ProtectionCheckEvent event) {
        // Vanilla ChestShop shop-member protection always runs first; both handlers only
        // ever set DENY (and self-guard on it), so the result is "deny if either denies".
        io.paradaux.chestshop.plugins.ChestShop.onProtectionCheck(event);
        Consumer<ProtectionCheckEvent> wg = worldGuardProtection;
        if (wg != null) {
            wg.accept(event);
        }
        return event.getResult() != Event.Result.DENY;
    }

    /**
     * Whether a shop may be created at {@code sign}/{@code chest} by {@code player}.
     * Defaults to allowed; WorldGuard then GriefPrevention may disallow. Both ran with
     * {@code ignoreCancelled=true} (skip once disallowed), so each only runs while the
     * build is still allowed — WorldGuard first (it is loaded first).
     */
    public boolean canBuild(Player player, @Nullable Location chest, Location sign) {
        BuildPermissionEvent event = new BuildPermissionEvent(player, chest, sign);
        Consumer<BuildPermissionEvent> wgb = worldGuardBuilding;
        if (wgb != null && event.isAllowed()) {
            wgb.accept(event);
        }
        Consumer<BuildPermissionEvent> gpb = griefPreventionBuilding;
        if (gpb != null && event.isAllowed()) {
            gpb.accept(event);
        }
        return event.isAllowed();
    }
}
