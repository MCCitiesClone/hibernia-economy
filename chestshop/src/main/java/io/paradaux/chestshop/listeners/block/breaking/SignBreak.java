package io.paradaux.chestshop.listeners.block.breaking;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.utils.BlockUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.ChestShopConfiguration;
import io.paradaux.chestshop.context.ShopDestroyedContext;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.ShopService;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.ShopBlockUtil;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getState;
import static io.paradaux.chestshop.utils.BlockUtil.getAttachedBlock;
import static io.paradaux.chestshop.utils.BlockUtil.isSign;
import static io.paradaux.chestshop.permission.Permissions.OTHER_NAME_DESTROY;

/**
 * @author Acrobot
 */
@Singleton
public class SignBreak implements Listener {
    private static final BlockFace[] SIGN_CONNECTION_FACES = {BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP};
    public static final String METADATA_NAME = "shop_destroyer";

    private final AccountService accounts;
    private final ShopService shops;
    private final Message message;
    private final ChestShopConfiguration config;
    private final ChestShopSign chestShopSign;
    private final ShopBlockUtil shopBlockUtil;

    @Inject
    public SignBreak(AccountService accounts, ShopService shops, Message message,
                     ChestShopConfiguration config, ChestShopSign chestShopSign, ShopBlockUtil shopBlockUtil) {
        this.accounts = accounts;
        this.shops = shops;
        this.message = message;
        this.config = config;
        this.chestShopSign = chestShopSign;
        this.shopBlockUtil = shopBlockUtil;
    }

    public void handlePhysicsBreak(Block block) {
        if (!BlockUtil.isSign(block)) {
            return;
        }

        Sign sign = (Sign) getState(block, false);
        Block attachedBlock = BlockUtil.getAttachedBlock(sign);

        if (attachedBlock.getType() == Material.AIR && chestShopSign.isValid(sign)) {
            sendShopDestroyed((Sign) block.getState(), block.hasMetadata(METADATA_NAME)
                    ? (Player) block.getMetadata(METADATA_NAME).get(0).value()
                    : null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignBreak(BlockBreakEvent event) {
        if (!canBlockBeBroken(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
            message.send(event.getPlayer(), "chestshop.ACCESS_DENIED");
            if (isSign(event.getBlock())) {
                event.getBlock().getState().update();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBrokenSign(BlockBreakEvent event) {
        if (chestShopSign.isValid(event.getBlock())) {
            sendShopDestroyed((Sign) event.getBlock().getState(), event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (!canBlockBeBroken(block, null)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (!canBlockBeBroken(block, null)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (event.blockList() == null || !config.isUseBuiltInProtection()) {
            return;
        }

        for (Block block : event.blockList()) {
            if (!canBlockBeBroken(block, null)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockBurnEvent event) {
        if (!canBlockBeBroken(event.getBlock(), null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!canBlockBeBroken(event.getBlock(), null)) {
            event.setCancelled(true);
        }
    }

    public boolean canBlockBeBroken(Block block, Player breaker) {
        List<Sign> attachedSigns = getAttachedSigns(block);
        List<Sign> brokenBlocks = new LinkedList<Sign>();

        boolean canBeBroken = true;

        for (Sign sign : attachedSigns) {

            if (!canBeBroken || !chestShopSign.isValid(sign)) {
                continue;
            }

            if (config.isTurnOffSignProtection() || canDestroyShop(breaker, ChestShopSign.getOwner(sign))) {
                brokenBlocks.add(sign);
            } else {
                canBeBroken = false;
            }
        }

        if (!canBeBroken) {
            return false;
        }

        for (Sign sign : brokenBlocks) {
            sign.setMetadata(METADATA_NAME, new FixedMetadataValue(ChestShop.getPlugin(), breaker));
        }

        return true;
    }

    private boolean canDestroyShop(Player player, String name) {
        return player != null && accounts.canUseName(player, OTHER_NAME_DESTROY, name);
    }

    public void sendShopDestroyed(Sign sign, Player player) {
        Container connectedContainer = shopBlockUtil.findConnectedContainer(sign.getBlock());

        shops.onDestroyed(new ShopDestroyedContext(player, sign, connectedContainer));
    }

    private static List<Sign> getAttachedSigns(Block block) {
        if (block == null) {
            return new ArrayList<>();
        }

        if (isSign(block)) {
            return Collections.singletonList((Sign) block.getState());
        } else {
            List<Sign> attachedSigns = new LinkedList<Sign>();

            for (BlockFace face : SIGN_CONNECTION_FACES) {
                Block relative = block.getRelative(face);

                if (!isSign(relative)) {
                    continue;
                }

                Sign sign = (Sign) relative.getState();

                if (getAttachedBlock(sign).equals(block)) {
                    attachedSigns.add(sign);
                }
            }

            return attachedSigns;
        }
    }
}
