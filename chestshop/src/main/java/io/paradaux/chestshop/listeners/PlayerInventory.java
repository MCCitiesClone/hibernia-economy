package io.paradaux.chestshop.listeners;

import io.paradaux.chestshop.services.ShopBlockService;
import com.google.inject.Inject;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.permission.Permissions;
import io.paradaux.chestshop.services.Security;
import io.paradaux.chestshop.services.InfoService;
import io.paradaux.chestshop.services.ChestShopSign;
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
    private final ChestShopConfiguration config;
    private final ChestShopSign chestShopSign;
    private final ShopBlockService shopBlockService;

    @Inject
    public PlayerInventory(InfoService info, Message message, Security security,
                           ChestShopConfiguration config, ChestShopSign chestShopSign, ShopBlockService shopBlockService) {
        this.info = info;
        this.message = message;
        this.security = security;
        this.config = config;
        this.chestShopSign = chestShopSign;
        this.shopBlockService = shopBlockService;
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!config.isTurnOffDefaultProtectionWhenProtectedExternally()) {
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
            if (shopBlockService.isShopBlock(container)) {
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
                    Sign sign = shopBlockService.getConnectedSign(container);
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
