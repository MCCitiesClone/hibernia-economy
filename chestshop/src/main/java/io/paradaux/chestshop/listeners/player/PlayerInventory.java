package io.paradaux.chestshop.listeners.player;

import com.google.inject.Inject;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.permission.Permissions;
import io.paradaux.chestshop.services.Security;
import io.paradaux.chestshop.services.InfoService;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.ShopBlockUtil;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getHolder;
import static io.paradaux.chestshop.utils.ImplementationAdapter.getLeftSide;
import static io.paradaux.chestshop.utils.ImplementationAdapter.getRightSide;

/**
 * @author Acrobot
 */
public class PlayerInventory implements Listener {

    private final InfoService info;
    private final Message message;
    private final Security security;

    @Inject
    public PlayerInventory(InfoService info, Message message, Security security) {
        this.info = info;
        this.message = message;
        this.security = security;
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!Properties.TURN_OFF_DEFAULT_PROTECTION_WHEN_PROTECTED_EXTERNALLY) {
            return;
        }

        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        InventoryHolder holder = getHolder(event.getInventory(), false);
        if (!(holder instanceof BlockState) && !(holder instanceof DoubleChest)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        List<Block> containers = new ArrayList<>();

        if (holder instanceof DoubleChest) {
            InventoryHolder leftSide = getLeftSide((DoubleChest) holder, false);
            if (leftSide instanceof BlockState) {
                containers.add(((BlockState) leftSide).getBlock());
            }
            InventoryHolder rightSide = getRightSide((DoubleChest) holder, false);
            if (rightSide instanceof BlockState) {
                containers.add(((BlockState) rightSide).getBlock());
            }
        } else {
            containers.add(((BlockState) holder).getBlock());
        }

        boolean canAccess = false;
        for (Block container : containers) {
            if (ChestShopSign.isShopBlock(container)) {
                if (security.canView(player, container, false)) {
                    canAccess = true;
                }
            } else {
                canAccess = true;
            }
        }

        if (!canAccess) {
            if (Permissions.has(player, Permissions.SHOPINFO)) {
                for (Block container : containers) {
                    Sign sign = ShopBlockUtil.getConnectedSign(container);
                    if (sign != null) {
                        info.showShopInfo((Player) event.getPlayer(), sign);
                    }
                }
            } else {
                message.send(event.getPlayer(), "chestshop.ACCESS_DENIED");
            }
            event.setCancelled(true);
        }
    }
}
