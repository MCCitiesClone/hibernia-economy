package io.paradaux.chestshop.listeners.block.breaking;

import io.paradaux.chestshop.utils.BlockUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.ShopDestroyedEvent;
import io.paradaux.chestshop.listeners.block.breaking.attached.PhysicsBreak;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getState;
import static io.paradaux.chestshop.utils.BlockUtil.getAttachedBlock;
import static io.paradaux.chestshop.utils.BlockUtil.isSign;
import static io.paradaux.chestshop.Permission.OTHER_NAME_DESTROY;

/**
 * @author Acrobot
 */
public class SignBreak implements Listener {
    private static final BlockFace[] SIGN_CONNECTION_FACES = {BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP};
    public static final String METADATA_NAME = "shop_destroyer";

    public SignBreak() {
        try {
            Class.forName("com.destroystokyo.paper.event.block.BlockDestroyEvent");
            ChestShop.getPlugin().registerEvent((Listener) Class.forName("io.paradaux.chestshop.listeners.block.breaking.attached.PaperBlockDestroy").getDeclaredConstructor().newInstance());
            ChestShop.getBukkitLogger().info("Using Paper's BlockDestroyEvent instead of the BlockPhysicsEvent!");
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            ChestShop.getPlugin().registerEvent(new PhysicsBreak());
        }
    }

    public static void handlePhysicsBreak(Block block) {
        if (!BlockUtil.isSign(block)) {
            return;
        }

        Sign sign = (Sign) getState(block, false);
        Block attachedBlock = BlockUtil.getAttachedBlock(sign);

        if (attachedBlock.getType() == Material.AIR && ChestShopSign.isValid(sign)) {
            sendShopDestroyedEvent((Sign) block.getState(), block.hasMetadata(METADATA_NAME)
                    ? (Player) block.getMetadata(METADATA_NAME).get(0).value()
                    : null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public static void onSignBreak(BlockBreakEvent event) {
        if (!canBlockBeBroken(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
            ChestShop.message().send(event.getPlayer(), "chestshop.ACCESS_DENIED");
            if (isSign(event.getBlock())) {
                event.getBlock().getState().update();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public static void onBrokenSign(BlockBreakEvent event) {
        if (ChestShopSign.isValid(event.getBlock())) {
            sendShopDestroyedEvent((Sign) event.getBlock().getState(), event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public static void onBlockPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (!canBlockBeBroken(block, null)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public static void onBlockPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (!canBlockBeBroken(block, null)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public static void onExplosion(EntityExplodeEvent event) {
        if (event.blockList() == null || !Properties.USE_BUILT_IN_PROTECTION) {
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
    public static void onIgnite(BlockBurnEvent event) {
        if (!canBlockBeBroken(event.getBlock(), null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public static void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!canBlockBeBroken(event.getBlock(), null)) {
            event.setCancelled(true);
        }
    }

    public static boolean canBlockBeBroken(Block block, Player breaker) {
        List<Sign> attachedSigns = getAttachedSigns(block);
        List<Sign> brokenBlocks = new LinkedList<Sign>();

        boolean canBeBroken = true;

        for (Sign sign : attachedSigns) {

            if (!canBeBroken || !ChestShopSign.isValid(sign)) {
                continue;
            }

            if (Properties.TURN_OFF_SIGN_PROTECTION || canDestroyShop(breaker, ChestShopSign.getOwner(sign))) {
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

    private static boolean canDestroyShop(Player player, String name) {
        return player != null && ChestShop.accounts().canUseName(player, OTHER_NAME_DESTROY, name);
    }

    public static void sendShopDestroyedEvent(Sign sign, Player player) {
        Container connectedContainer = uBlock.findConnectedContainer(sign.getBlock());

        Event event = new ShopDestroyedEvent(player, sign, connectedContainer);
        ChestShop.callEvent(event);
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
