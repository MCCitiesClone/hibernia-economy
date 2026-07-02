package io.paradaux.chestshop.listeners;

import io.paradaux.chestshop.services.ShopBlockService;
import com.google.inject.Inject;
import io.paradaux.chestshop.permission.Permissions;
import io.paradaux.chestshop.services.Security;
import io.paradaux.chestshop.services.ChestShopSign;
import io.paradaux.chestshop.utils.ShopBlockUtil;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Acrobot
 */
public class BlockPlace implements Listener {

    private final Message message;
    private final Security security;
    private final ChestShopSign chestShopSign;
    private final ShopBlockService shopBlockService;

    @Inject
    public BlockPlace(Message message, Security security, ChestShopSign chestShopSign, ShopBlockService shopBlockService) {
        this.message = message;
        this.security = security;
        this.chestShopSign = chestShopSign;
        this.shopBlockService = shopBlockService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onContainerPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();

        if (!shopBlockService.couldBeShopContainer(placed)) {
            return;
        }

        Player player = event.getPlayer();

        if (Permissions.has(player, Permissions.ADMIN)) {
            return;
        }

        if (!security.canAccess(player, placed)) {
            message.send(event.getPlayer(), "chestshop.ACCESS_DENIED");
            event.setCancelled(true);
            return;
        }

        Block neighbor = ShopBlockUtil.findNeighbor(placed);

        if (neighbor != null && !security.canAccess(event.getPlayer(), neighbor)) {
            message.send(event.getPlayer(), "chestshop.ACCESS_DENIED");
            event.setCancelled(true);
        }

    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceAgainstSign(BlockPlaceEvent event) {
        Block against = event.getBlockAgainst();

        if (!chestShopSign.isValid(against)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperDropperPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();

        List<BlockFace> searchDirections = new ArrayList<>();
        switch (placed.getType()) {
            case HOPPER:
                searchDirections.add(BlockFace.UP);
                searchDirections.add(((Directional) placed.getBlockData()).getFacing());
                break;
            case DROPPER:
                searchDirections.add(((Directional) placed.getBlockData()).getFacing());
                break;
            default:
                return;
        }

        for (BlockFace face : searchDirections) {
            Block relative = placed.getRelative(face);

            if (!shopBlockService.couldBeShopContainer(relative)) {
                continue;
            }

            if (!security.canAccess(event.getPlayer(), relative)) {
                message.send(event.getPlayer(), "chestshop.ACCESS_DENIED");
                event.setCancelled(true);
                return;
            }
        }
    }
}
