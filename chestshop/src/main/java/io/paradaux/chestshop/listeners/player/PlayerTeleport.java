package io.paradaux.chestshop.listeners.player;

import com.google.inject.Inject;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryHolder;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getHolder;

/**
 * A fix for a CraftBukkit bug.
 *
 * @author Acrobot
 */
public class PlayerTeleport implements Listener {

    private final ChestShopSign chestShopSign;

    @Inject
    public PlayerTeleport(ChestShopSign chestShopSign) {
        this.chestShopSign = chestShopSign;
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        InventoryHolder holder = getHolder(event.getPlayer().getOpenInventory().getTopInventory(), false);
        if (!(holder instanceof BlockState)) {
            return;
        }

        Block container;
        if (holder instanceof DoubleChest) {
            container = ((DoubleChest) holder).getLocation().getBlock();
        } else {
            container = ((BlockState) holder).getBlock();
        }

        if (chestShopSign.isShopBlock(container)) {
            event.getPlayer().closeInventory();
        }
    }
}
