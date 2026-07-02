package io.paradaux.chestshop.services.impl;

import io.paradaux.chestshop.services.ProtectionService;
import com.google.inject.Singleton;
import io.paradaux.chestshop.model.BuildPermission;
import io.paradaux.chestshop.model.ProtectionCheck;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Owns shop/chest protection checks, replacing the {@code ProtectionCheck} and
 * {@code BuildPermission} bus with direct, ordered calls. The vanilla shop-member
 * check ({@link io.paradaux.chestshop.integration.VanillaShopProtection#onProtectionCheck}) always runs;
 * the optional WorldGuard/GriefPrevention integrations run after it only when those
 * plugins are hooked and their config flags are on — {@code Dependencies} registers them
 * here as method references (so this service never names the {@code com.sk89q}/
 * {@code me.ryanhamshire} classes, keeping them off the call path when the plugin is
 * absent, exactly as the old conditional listener registration did).
 *
 * <p>Block-level protection (the LWC/Lockette integrations) was removed, so no provider
 * ever claims shop blocks — they are never independently protected, and that event was
 * dropped entirely.
 */
@Singleton
public class ProtectionServiceImpl implements ProtectionService {

    private final io.paradaux.chestshop.integration.VanillaShopProtection vanillaProtection;

    @com.google.inject.Inject
    public ProtectionServiceImpl(io.paradaux.chestshop.integration.VanillaShopProtection vanillaProtection) {
        this.vanillaProtection = vanillaProtection;
    }

    @Nullable private volatile Consumer<ProtectionCheck> worldGuardProtection;
    @Nullable private volatile Consumer<BuildPermission> worldGuardBuilding;
    @Nullable private volatile Consumer<BuildPermission> griefPreventionBuilding;

    /** Hook WorldGuard region/built-in protection into access checks (WORLDGUARD_USE_PROTECTION). */
    @Override
    public void setWorldGuardProtection(Consumer<ProtectionCheck> check) {
        this.worldGuardProtection = check;
    }

    /** Hook WorldGuard region gating into shop-creation build checks (WORLDGUARD_INTEGRATION). */
    @Override
    public void setWorldGuardBuilding(Consumer<BuildPermission> check) {
        this.worldGuardBuilding = check;
    }

    /** Hook GriefPrevention claim gating into shop-creation build checks (GRIEFPREVENTION_INTEGRATION). */
    @Override
    public void setGriefPreventionBuilding(Consumer<BuildPermission> check) {
        this.griefPreventionBuilding = check;
    }

    /** Whether {@code player} may access {@code block} (vanilla shop membership + WorldGuard). */
    @Override
    public boolean canAccess(Block block, Player player, boolean ignoreBuiltInProtection) {
        return runProtectionCheck(new ProtectionCheck(block, player, ignoreBuiltInProtection));
    }

    /** Whether {@code player} may view {@code block} (as {@link #canAccess} but without the manage check). */
    @Override
    public boolean canView(Block block, Player player, boolean ignoreBuiltInProtection) {
        return runProtectionCheck(new ProtectionCheck(block, player, ignoreBuiltInProtection, false));
    }

    private boolean runProtectionCheck(ProtectionCheck event) {
        // Vanilla ChestShop shop-member protection always runs first; both handlers only
        // ever set DENY (and self-guard on it), so the result is "deny if either denies".
        vanillaProtection.onProtectionCheck(event);
        Consumer<ProtectionCheck> wg = worldGuardProtection;
        if (wg != null) {
            wg.accept(event);
        }
        return event.getResult() != Event.Result.DENY;
    }

    @Override
    public boolean canBuild(Player player, @Nullable Location chest, Location sign) {
        BuildPermission event = new BuildPermission(player, chest, sign);
        Consumer<BuildPermission> wgb = worldGuardBuilding;
        if (wgb != null && event.isAllowed()) {
            wgb.accept(event);
        }
        Consumer<BuildPermission> gpb = griefPreventionBuilding;
        if (gpb != null && event.isAllowed()) {
            gpb.accept(event);
        }
        return event.isAllowed();
    }
}
