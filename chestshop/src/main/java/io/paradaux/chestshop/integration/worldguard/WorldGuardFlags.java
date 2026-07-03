package io.paradaux.chestshop.integration.worldguard;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;

/**
 * @author Brokkonaut
 */
public class WorldGuardFlags {
    public static final StateFlag ENABLE_SHOP;

    static {
        StateFlag enableShop;
        try {
            enableShop = new StateFlag("allow-shop", false);
            WorldGuard.getInstance().getFlagRegistry().register(enableShop);
        } catch (FlagConflictException | IllegalStateException e) {
            enableShop = (StateFlag) WorldGuard.getInstance().getFlagRegistry().get("allow-shop");
        }
        ENABLE_SHOP = enableShop;
    }

    private WorldGuardFlags() {
    }

    /**
     * Force the {@code allow-shop} flag registration (runs the static initialiser). WorldGuard
     * locks its flag registry once it enables, so this must be called from {@code onLoad}, before
     * that — earlier than the Guice injector exists, which is why it stays a plain static call.
     */
    public static void register() {
        ENABLE_SHOP.getName();
    }
}