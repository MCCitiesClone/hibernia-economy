package io.paradaux.chestshop.listeners;

import com.google.inject.Inject;
import io.paradaux.chestshop.configuration.ChestShopConfiguration;
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
    private final ChestShopConfiguration config;
    private final ChestShopSign chestShopSign;

    @Inject
    public ItemMoveListener(StockCounterModule stockCounter, ChestShopConfiguration config, ChestShopSign chestShopSign) {
        this.stockCounter = stockCounter;
        this.config = config;
        this.chestShopSign = chestShopSign;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onItemMove(InventoryMoveItemEvent event) {
        InventoryHolder destinationHolder = getHolder(event.getDestination(), false);

        if (!config.isTurnOffHopperProtection() && !(destinationHolder instanceof BlockState)) {
            InventoryHolder sourceHolder = getHolder(event.getSource(), false);
            if (chestShopSign.isShopBlock(sourceHolder)) {
                event.setCancelled(true);
                return;
            }
        }
        if (config.isUseStockCounter() && chestShopSign.isShopBlock(destinationHolder)) {
            stockCounter.updateCounterOnItemMoveEvent(event.getItem(), destinationHolder);
        }
    }


}
