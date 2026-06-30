package io.paradaux.chestshop.listeners.item;

import com.google.inject.Inject;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.listeners.modules.StockCounterModule;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.InventoryHolder;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getHolder;

/**
 * @author Acrobot
 */
public class ItemMoveListener implements Listener {

    private final StockCounterModule stockCounter;

    @Inject
    public ItemMoveListener(StockCounterModule stockCounter) {
        this.stockCounter = stockCounter;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onItemMove(InventoryMoveItemEvent event) {
        InventoryHolder destinationHolder = getHolder(event.getDestination(), false);

        if (!Properties.TURN_OFF_HOPPER_PROTECTION && !(destinationHolder instanceof BlockState)) {
            InventoryHolder sourceHolder = getHolder(event.getSource(), false);
            if (ChestShopSign.isShopBlock(sourceHolder)) {
                event.setCancelled(true);
                return;
            }
        }
        if (Properties.USE_STOCK_COUNTER && ChestShopSign.isShopBlock(destinationHolder)) {
            stockCounter.updateCounterOnItemMoveEvent(event.getItem(), destinationHolder);
        }
    }


}
