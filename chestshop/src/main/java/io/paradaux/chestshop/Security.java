package io.paradaux.chestshop;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.ProtectionService;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.BlockUtil;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getState;

/**
 * Shop-block protection facade over {@link ProtectionService}. Injected like any
 * other collaborator (PAR-282) — the former static {@code ChestShop.protection()}
 * locator hops are gone.
 *
 * @author Acrobot
 */
@Singleton
public class Security {
    private static final BlockFace[] SIGN_CONNECTION_FACES = {BlockFace.UP, BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH};
    private static final BlockFace[] BLOCKS_AROUND = {BlockFace.UP, BlockFace.DOWN, BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH};

    private final ProtectionService protection;
    private final AccountService accounts;

    @Inject
    public Security(ProtectionService protection, AccountService accounts) {
        this.protection = protection;
        this.accounts = accounts;
    }

    public boolean canAccess(Player player, Block block) {
        return canAccess(player, block, false);
    }

    public boolean canAccess(Player player, Block block, boolean ignoreDefaultProtection) {
        return protection.canAccess(block, player, ignoreDefaultProtection);
    }

    public boolean canView(Player player, Block block, boolean ignoreDefaultProtection) {
        return protection.canView(block, player, ignoreDefaultProtection);
    }

    public boolean canPlaceSign(Player player, Sign sign) {
        Block baseBlock = BlockUtil.getAttachedBlock(sign);

        if (!Properties.ALLOW_MULTIPLE_SHOPS_AT_ONE_BLOCK && anotherShopFound(baseBlock, sign.getBlock(), player)) {
            return false;
        }

        return canBePlaced(player, sign.getBlock());
    }

    private boolean canBePlaced(Player player, Block sign) {
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

    private boolean anotherShopFound(Block baseBlock, Block signBlock, Player player) {
        for (BlockFace face : SIGN_CONNECTION_FACES) {
            Block block = baseBlock.getRelative(face);

            if (block.equals(signBlock) || !BlockUtil.isSign(block)) {
                continue;
            }

            Sign sign = (Sign) getState(block, false);

            if (!ChestShopSign.isValid(sign) || !BlockUtil.getAttachedBlock(sign).equals(baseBlock)) {
                continue;
            }

            if (!accounts.isOwner(player, sign)) {
                return true;
            }
        }
        return false;
    }
}
