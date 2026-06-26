package io.paradaux.chestshop.plugins;

import io.paradaux.chestshop.events.ChestShopReloadEvent;
import io.paradaux.chestshop.events.protection.ProtectionCheckEvent;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getState;
import static io.paradaux.chestshop.utils.BlockUtil.isSign;

/**
 * @author Acrobot
 */
public class ChestShop implements Listener {

    @EventHandler
    public static void onReload(ChestShopReloadEvent event) {
        io.paradaux.chestshop.ChestShop.getPlugin().loadConfig();
    }

    // Invoked directly by ProtectionService (was a NORMAL ProtectionCheckEvent listener).
    public static void onProtectionCheck(ProtectionCheckEvent event) {
        if (event.getResult() == Event.Result.DENY || event.isBuiltInProtectionIgnored()) {
            return;
        }

        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (!canAccess(player, block)) {
            event.setResult(Event.Result.DENY);
        }
    }

    public static boolean canAccess(Player player, Block block) {
        if (!canBeProtected(block)) {
            return true;
        }

        if (isSign(block)) {
            Sign sign = (Sign) getState(block, false);

            if (!ChestShopSign.isValid(sign)) {
                return true;
            }

            if (!isShopMember(player, sign)) {
                return false;
            }
        }

        if (uBlock.couldBeShopContainer(block)) {
            Sign sign = uBlock.getConnectedSign(block);

            if (sign != null && !isShopMember(player, sign)) {
                return false;
            }
        }

        return true;
    }

    private static boolean canBeProtected(Block block) {
        return isSign(block) || uBlock.couldBeShopContainer(block);
    }

    private static boolean isShopMember(Player player, Sign sign) {
        return ChestShopSign.hasPermission(player, Permission.OTHER_NAME_ACCESS, sign);
    }
}
