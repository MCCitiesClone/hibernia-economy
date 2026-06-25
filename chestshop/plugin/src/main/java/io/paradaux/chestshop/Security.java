package io.paradaux.chestshop;

import io.paradaux.chestshop.breeze.utils.BlockUtil;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.events.AccountQueryEvent;
import io.paradaux.chestshop.events.protection.ProtectBlockEvent;
import io.paradaux.chestshop.events.protection.ProtectionCheckEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.UUID;

import static io.paradaux.chestshop.breeze.utils.ImplementationAdapter.getState;

/**
 * @author Acrobot
 */
public class Security {
    private static final BlockFace[] SIGN_CONNECTION_FACES = {BlockFace.UP, BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH};
    private static final BlockFace[] BLOCKS_AROUND = {BlockFace.UP, BlockFace.DOWN, BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH};

    public static boolean protect(Player player, Block block) {
        return protect(player, block, player.getUniqueId());
    }

    public static boolean protect(Player player, Block block, UUID protectionOwner) {
        return protect(player, block, protectionOwner, Type.PRIVATE);
    }

    public static boolean protect(Player player, Block block, UUID protectionOwner, Type type) {
        ProtectBlockEvent event = ChestShop.callEvent(new ProtectBlockEvent(block, player, protectionOwner, type));

        return event.isProtected();
    }

    public static boolean canAccess(Player player, Block block) {
        return canAccess(player, block, false);
    }

    public static boolean canAccess(Player player, Block block, boolean ignoreDefaultProtection) {
        ProtectionCheckEvent event = ChestShop.callEvent(new ProtectionCheckEvent(block, player, ignoreDefaultProtection));

        return event.getResult() != Event.Result.DENY;
    }

    public static boolean canView(Player player, Block block, boolean ignoreDefaultProtection) {
        ProtectionCheckEvent event = ChestShop.callEvent(new ProtectionCheckEvent(block, player, ignoreDefaultProtection, false));

        return event.getResult() != Event.Result.DENY;
    }

    public static boolean canPlaceSign(Player player, Sign sign) {
        Block baseBlock = BlockUtil.getAttachedBlock(sign);

        if (!Properties.ALLOW_MULTIPLE_SHOPS_AT_ONE_BLOCK && anotherShopFound(baseBlock, sign.getBlock(), player)) {
            return false;
        }

        return canBePlaced(player, sign.getBlock());
    }

    private static boolean canBePlaced(Player player, Block sign) {
        for (BlockFace face : BLOCKS_AROUND) {
            Block block = sign.getRelative(face);

            if (!uBlock.couldBeShopContainer(block)) {
                continue;
            }
            if (!canAccess(player, block)) {
                return false;
            }
        }

        return true;
    }

    private static boolean anotherShopFound(Block baseBlock, Block signBlock, Player player) {
        for (BlockFace face : SIGN_CONNECTION_FACES) {
            Block block = baseBlock.getRelative(face);

            if (block.equals(signBlock) || !BlockUtil.isSign(block)) {
                continue;
            }

            Sign sign = (Sign) getState(block, false);

            if (!ChestShopSign.isValid(sign) || !BlockUtil.getAttachedBlock(sign).equals(baseBlock)) {
                continue;
            }

            if (!ChestShopSign.isOwner(player, sign)) {
                return true;
            }
        }
        return false;
    }

    public enum Type {
        PUBLIC,
        PRIVATE,
        DONATION,
        DISPLAY
    }
}
