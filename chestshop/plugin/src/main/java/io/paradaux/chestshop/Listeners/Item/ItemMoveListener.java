package io.paradaux.chestshop.Listeners.Item;

import io.paradaux.chestshop.Configuration.Properties;
import io.paradaux.chestshop.Listeners.Modules.StockCounterModule;
import io.paradaux.chestshop.Signs.ChestShopSign;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.InventoryHolder;

import static io.paradaux.chestshop.breeze.Utils.ImplementationAdapter.getHolder;

/**
 * @author Acrobot
 */
public class ItemMoveListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public static void onItemMove(InventoryMoveItemEvent event) {
        InventoryHolder destinationHolder = getHolder(event.getDestination(), false);

        if (!Properties.TURN_OFF_HOPPER_PROTECTION && !(destinationHolder instanceof BlockState)) {
            InventoryHolder sourceHolder = getHolder(event.getSource(), false);
            if (ChestShopSign.isShopBlock(sourceHolder)) {
                event.setCancelled(true);
                return;
            }
        }
        if (Properties.USE_STOCK_COUNTER && ChestShopSign.isShopBlock(destinationHolder)) {
            StockCounterModule.updateCounterOnItemMoveEvent(event.getItem(), destinationHolder);
        }
    }


}
