package io.paradaux.chestshop.permission;

import io.paradaux.chestshop.AdminBypass;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * ChestShop permission nodes + the runtime checks that the framework's command
 * {@code @Permission} annotation can't express: the per-material buy/sell/create nodes
 * (built by concatenation), the {@code othername} wildcard / set-false rules, and the
 * {@link AdminBypass} demotion. Commands gate themselves with HiberniaFramework's
 * {@code @Permission} directly (no enum); these helpers are only for the imperative
 * checks in the services and listeners.
 *
 * @author Acrobot
 */
public final class Permissions {

    public static final String SHOP_CREATION_BUY = "ChestShop.shop.create.buy";
    public static final String SHOP_CREATION_BUY_ID = "ChestShop.shop.create.buy.";
    public static final String SHOP_CREATION_SELL = "ChestShop.shop.create.sell";
    public static final String SHOP_CREATION_SELL_ID = "ChestShop.shop.create.sell.";
    public static final String SHOP_CREATION = "ChestShop.shop.create";
    public static final String SHOP_CREATION_ID = "ChestShop.shop.create.";

    public static final String BUY = "ChestShop.shop.buy";
    public static final String BUY_ID = "ChestShop.shop.buy.";
    public static final String SELL = "ChestShop.shop.sell";
    public static final String SELL_ID = "ChestShop.shop.sell.";

    public static final String ADMIN = "ChestShop.admin";
    public static final String ADMIN_SHOP = "ChestShop.adminshop";
    public static final String MOD = "ChestShop.mod";
    public static final String OTHER_NAME = "ChestShop.name";
    public static final String OTHER_NAME_CREATE = "ChestShop.othername.create";
    public static final String OTHER_NAME_DESTROY = "ChestShop.othername.destroy";
    public static final String OTHER_NAME_ACCESS = "ChestShop.othername.access";
    public static final String GROUP = "ChestShop.group.";

    public static final String NOFEE = "ChestShop.nofee";
    public static final String NO_BUY_TAX = "ChestShop.notax.buy";
    public static final String NO_SELL_TAX = "ChestShop.notax.sell";

    public static final String NOTIFY_TOGGLE = "ChestShop.toggle";
    public static final String ITEMINFO = "ChestShop.iteminfo";
    public static final String SHOPINFO = "ChestShop.shopinfo";

    private Permissions() {}

    public static boolean has(CommandSender sender, String node) {
        // An admin who ran /chestshop bypass to "play" forfeits the elevated staff
        // nodes until they turn bypass back on, so every admin-gated path treats
        // them as a normal player (AdminBypass).
        if (sender instanceof Player player && AdminBypass.isDisabled(player) && AdminBypass.isGated(node)) {
            return false;
        }
        return sender.hasPermission(node) || sender.hasPermission(node.toLowerCase(Locale.ROOT));
    }

    public static boolean otherName(Player player, String name) {
        return otherName(player, OTHER_NAME, name);
    }

    public static boolean otherName(Player player, String base, String name) {
        boolean hasBase = !base.equals(OTHER_NAME) && otherName(player, OTHER_NAME, name);
        if (hasBase || has(player, base + ".*")) {
            return !hasPermissionSetFalse(player, base + "." + name)
                    && !hasPermissionSetFalse(player, base + "." + name.toLowerCase(Locale.ROOT));
        }
        return has(player, base + "." + name) || has(player, base + "." + name.toLowerCase(Locale.ROOT));
    }

    public static boolean hasPermissionSetFalse(CommandSender sender, String permission) {
        return (sender.isPermissionSet(permission) && !sender.hasPermission(permission))
                || (sender.isPermissionSet(permission.toLowerCase(Locale.ROOT)) && !sender.hasPermission(permission.toLowerCase(Locale.ROOT)));
    }
}
