package io.paradaux.chestshop.listeners.modules;

import io.paradaux.chestshop.breeze.utils.InventoryUtil;
import io.paradaux.chestshop.breeze.utils.MaterialUtil;
import io.paradaux.chestshop.breeze.utils.QuantityUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.ItemParseEvent;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.events.TransactionEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.IllegalFormatException;

import static io.paradaux.chestshop.breeze.utils.ImplementationAdapter.getHolder;
import static io.paradaux.chestshop.signs.ChestShopSign.QUANTITY_LINE;


/**
 * @author bricefrisco
 */
public class StockCounterModule implements Listener {
    private static final String PRICE_LINE_WITH_COUNT = "Q %d : C %d";

    @EventHandler(priority = EventPriority.HIGH)
    public static void onPreShopCreation(PreShopCreationEvent event) {
        int quantity;
        try {
            quantity = ChestShopSign.getQuantity(event.getSignLines());
        } catch (IllegalArgumentException invalidQuantity) {
            return;
        }

        if (QuantityUtil.quantityLineContainsCounter(ChestShopSign.getQuantityLine(event.getSignLines()))) {
            event.setSignLine(QUANTITY_LINE, Integer.toString(quantity));
        }

        if (!Properties.USE_STOCK_COUNTER
                || (Properties.FORCE_UNLIMITED_ADMIN_SHOP && ChestShopSign.isAdminShop(event.getSignLines()))) {
            return;
        }

        if (Properties.MAX_SHOP_AMOUNT > 99999) {
            ChestShop.getBukkitLogger().warning("Stock counter cannot be used if MAX_SHOP_AMOUNT is over 5 digits");
            return;
        }

        ItemStack itemTradedByShop = determineItemTradedByShop(ChestShopSign.getItem(event.getSignLines()));
        if (itemTradedByShop != null) {
            Container container = uBlock.findConnectedContainer(event.getSign());
            if (container != null) {
                event.setSignLine(QUANTITY_LINE, getQuantityLineWithCounter(quantity, itemTradedByShop, container.getInventory()));
            }
        }
    }

    @EventHandler
    public static void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST || event.getInventory().getLocation() == null) {
            return;
        }

        InventoryHolder holder = getHolder(event.getInventory(), false);
        if (!uBlock.couldBeShopContainer(holder)) {
            return;
        }

        for (Sign shopSign : uBlock.findConnectedShopSigns(holder)) {
            if (!Properties.USE_STOCK_COUNTER
                    || (Properties.FORCE_UNLIMITED_ADMIN_SHOP && ChestShopSign.isAdminShop(shopSign))) {
                if (QuantityUtil.quantityLineContainsCounter(ChestShopSign.getQuantityLine(shopSign))) {
                    removeCounterFromQuantityLine(shopSign);
                }
                continue;
            }

            if (Properties.MAX_SHOP_AMOUNT > 99999) {
                ChestShop.getBukkitLogger().warning("Stock counter cannot be used if MAX_SHOP_AMOUNT is over 5 digits");
                if (QuantityUtil.quantityLineContainsCounter(ChestShopSign.getQuantityLine(shopSign))) {
                    removeCounterFromQuantityLine(shopSign);
                }
                return;
            }

            updateCounterOnQuantityLine(shopSign, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public static void onTransaction(final TransactionEvent event) {
        String quantityLine = ChestShopSign.getQuantityLine(event.getSign());
        if (!Properties.USE_STOCK_COUNTER) {
            if (QuantityUtil.quantityLineContainsCounter(quantityLine)) {
                removeCounterFromQuantityLine(event.getSign());
            }
            return;
        }

        if (Properties.MAX_SHOP_AMOUNT > 99999) {
            ChestShop.getBukkitLogger().warning("Stock counter cannot be used if MAX_SHOP_AMOUNT is over 5 digits");
            if (QuantityUtil.quantityLineContainsCounter(quantityLine)) {
                removeCounterFromQuantityLine(event.getSign());
            }
            return;
        }

        if (Properties.FORCE_UNLIMITED_ADMIN_SHOP && ChestShopSign.isAdminShop(event.getSign())) {
            return;
        }

        for (Sign shopSign : uBlock.findConnectedShopSigns( getHolder(event.getOwnerInventory(), false))) {
            updateCounterOnQuantityLine(shopSign, event.getOwnerInventory());
        }
    }

    /**
     * Update the stock counter on the sign's quantity line
     * @param sign               The sign to update
     * @param chestShopInventory The inventory to search in
     * @param extraItems         The extra items to add in the search
     */
    public static void updateCounterOnQuantityLine(Sign sign, Inventory chestShopInventory, ItemStack... extraItems) {
        ItemStack itemTradedByShop = determineItemTradedByShop(sign);
        if (itemTradedByShop == null) {
            return;
        }

        int quantity;
        try {
            quantity = ChestShopSign.getQuantity(sign);
        } catch (IllegalFormatException invalidQuantity) {
            return;
        }

        int numTradedItemsInChest = InventoryUtil.getAmount(itemTradedByShop, chestShopInventory);

        for (ItemStack extraStack : extraItems) {
            if (!MaterialUtil.equals(extraStack, itemTradedByShop)) {
                continue;
            }

            numTradedItemsInChest += extraStack.getAmount();
        }

        // Skip the forced block update when the counter text is unchanged. This
        // runs per InventoryMoveItemEvent (every hopper tick into a shop), so
        // avoiding a no-op sign.update(true) keeps busy hopper setups off the
        // main-thread hot path.
        String counterLine = String.format(PRICE_LINE_WITH_COUNT, quantity, numTradedItemsInChest);
        if (counterLine.equals(sign.getLine(QUANTITY_LINE))) {
            return;
        }

        sign.setLine(QUANTITY_LINE, counterLine);
        sign.update(true);
    }

    public static void updateCounterOnItemMoveEvent(ItemStack toAdd, InventoryHolder destinationHolder) {
        Block shopBlock = ChestShopSign.getShopBlock(destinationHolder);
        Sign connectedSign = uBlock.getConnectedSign(shopBlock);

        updateCounterOnQuantityLine(connectedSign, destinationHolder.getInventory(), toAdd);
    }

    public static void removeCounterFromQuantityLine(Sign sign) {
        int quantity;
        try {
            quantity = ChestShopSign.getQuantity(sign);
        } catch (IllegalFormatException invalidQuantity) {
            return;
        }

        sign.setLine(QUANTITY_LINE, Integer.toString(quantity));
        sign.update(true);
    }

    public static String getQuantityLineWithCounter(int amount, ItemStack itemTransacted, Inventory chestShopInventory) {
        int numTransactionItemsInChest = InventoryUtil.getAmount(itemTransacted, chestShopInventory);

        return String.format(PRICE_LINE_WITH_COUNT, amount, numTransactionItemsInChest);
    }

    public static ItemStack determineItemTradedByShop(Sign sign) {
        return determineItemTradedByShop(ChestShopSign.getItem(sign));
    }

    public static ItemStack determineItemTradedByShop(String material) {
        ItemParseEvent parseEvent = new ItemParseEvent(material);
        Bukkit.getPluginManager().callEvent(parseEvent);
        return parseEvent.getItem();
    }
}
