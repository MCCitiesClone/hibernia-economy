package io.paradaux.chestshop.listeners.posttransaction;

import io.paradaux.chestshop.utils.LocationUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.TransactionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

import static io.paradaux.chestshop.utils.InventoryUtil.getItemCounts;
import static io.paradaux.chestshop.utils.ItemUtil.getName;
import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;

/**
 * @author Acrobot
 */
public class TransactionLogger implements Listener {
    private static final String BUY_MESSAGE = "%1$s bought %2$s for %3$.2f from %4$s at %5$s";
    private static final String SELL_MESSAGE = "%1$s sold %2$s for %3$.2f to %4$s at %5$s";

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public static void onTransaction(final TransactionEvent event) {
        String template = (event.getTransactionType() == BUY ? BUY_MESSAGE : SELL_MESSAGE);

        StringBuilder items = new StringBuilder(50);

        for (Map.Entry<ItemStack, Integer> entry : getItemCounts(event.getStock()).entrySet()) {
            items.append(entry.getValue()).append(' ').append(getName(entry.getKey()));
        }

        String message = String.format(template,
                event.getClient().getName(),
                items.toString(),
                event.getExactPrice(),
                event.getOwnerAccount().getName(),
                LocationUtil.locationToString(event.getSign().getLocation()));

        ChestShop.getShopLogger().info(message);
    }
}
