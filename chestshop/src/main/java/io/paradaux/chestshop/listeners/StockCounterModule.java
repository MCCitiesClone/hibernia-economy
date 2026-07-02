package io.paradaux.chestshop.listeners;

import io.paradaux.chestshop.services.ShopBlockService;
import io.paradaux.chestshop.services.InventoryService;
import io.paradaux.chestshop.services.MaterialService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.chestshop.utils.QuantityUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.model.PreShopCreationContext;
import io.paradaux.chestshop.model.TransactionContext;
import io.paradaux.chestshop.services.ChestShopSign;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.IllegalFormatException;

import static io.paradaux.chestshop.utils.ImplementationAdapter.getHolder;
import static io.paradaux.chestshop.services.ChestShopSign.QUANTITY_LINE;


/**
 * @author bricefrisco
 */
@Singleton
public class StockCounterModule implements Listener {
    private static final String PRICE_LINE_WITH_COUNT = "Q %d : C %d";

    private final ItemService items;
    private final ChestShopConfiguration config;
    private final ChestShopSign chestShopSign;
    private final ShopBlockService shopBlockService;
    private final InventoryService inventoryService;
    private final MaterialService materialService;

    @Inject
    public StockCounterModule(ItemService items, ChestShopConfiguration config, ChestShopSign chestShopSign,
                              ShopBlockService shopBlockService, InventoryService inventoryService, MaterialService materialService) {
        this.items = items;
        this.config = config;
        this.chestShopSign = chestShopSign;
        this.shopBlockService = shopBlockService;
        this.inventoryService = inventoryService;
        this.materialService = materialService;
    }

    // Invoked directly by ShopService#create (was a @HIGH PreShopCreationContext listener).
    public void onPreShopCreation(PreShopCreationContext event) {
        int quantity;
        try {
            quantity = ChestShopSign.getQuantity(event.getSignLines());
        } catch (IllegalArgumentException invalidQuantity) {
            return;
        }

        if (QuantityUtil.quantityLineContainsCounter(ChestShopSign.getQuantityLine(event.getSignLines()))) {
            event.setSignLine(QUANTITY_LINE, Integer.toString(quantity));
        }

        if (!config.isUseStockCounter()
                || (config.isForceUnlimitedAdminShop() && chestShopSign.isAdminShop(event.getSignLines()))) {
            return;
        }

        if (config.getMaxShopAmount() > 99999) {
            ChestShop.getBukkitLogger().warning("Stock counter cannot be used if MAX_SHOP_AMOUNT is over 5 digits");
            return;
        }

        ItemStack itemTradedByShop = determineItemTradedByShop(ChestShopSign.getItem(event.getSignLines()));
        if (itemTradedByShop != null) {
            Container container = shopBlockService.findConnectedContainer(event.getSign());
            if (container != null) {
                event.setSignLine(QUANTITY_LINE, getQuantityLineWithCounter(quantity, itemTradedByShop, container.getInventory()));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST || event.getInventory().getLocation() == null) {
            return;
        }

        InventoryHolder holder = getHolder(event.getInventory(), false);
        if (!shopBlockService.couldBeShopContainer(holder)) {
            return;
        }

        for (Sign shopSign : shopBlockService.findConnectedShopSigns(holder)) {
            if (!config.isUseStockCounter()
                    || (config.isForceUnlimitedAdminShop() && chestShopSign.isAdminShop(shopSign))) {
                if (QuantityUtil.quantityLineContainsCounter(ChestShopSign.getQuantityLine(shopSign))) {
                    removeCounterFromQuantityLine(shopSign);
                }
                continue;
            }

            if (config.getMaxShopAmount() > 99999) {
                ChestShop.getBukkitLogger().warning("Stock counter cannot be used if MAX_SHOP_AMOUNT is over 5 digits");
                if (QuantityUtil.quantityLineContainsCounter(ChestShopSign.getQuantityLine(shopSign))) {
                    removeCounterFromQuantityLine(shopSign);
                }
                return;
            }

            updateCounterOnQuantityLine(shopSign, event.getInventory());
        }
    }

    // Invoked directly by TransactionService#process (was a @HIGH TransactionContext listener).
    public void onTransaction(final TransactionContext event) {
        String quantityLine = ChestShopSign.getQuantityLine(event.getSign());
        if (!config.isUseStockCounter()) {
            if (QuantityUtil.quantityLineContainsCounter(quantityLine)) {
                removeCounterFromQuantityLine(event.getSign());
            }
            return;
        }

        if (config.getMaxShopAmount() > 99999) {
            ChestShop.getBukkitLogger().warning("Stock counter cannot be used if MAX_SHOP_AMOUNT is over 5 digits");
            if (QuantityUtil.quantityLineContainsCounter(quantityLine)) {
                removeCounterFromQuantityLine(event.getSign());
            }
            return;
        }

        if (config.isForceUnlimitedAdminShop() && chestShopSign.isAdminShop(event.getSign())) {
            return;
        }

        for (Sign shopSign : shopBlockService.findConnectedShopSigns( getHolder(event.getOwnerInventory(), false))) {
            updateCounterOnQuantityLine(shopSign, event.getOwnerInventory());
        }
    }

    /**
     * Update the stock counter on the sign's quantity line
     * @param sign               The sign to update
     * @param chestShopInventory The inventory to search in
     * @param extraItems         The extra items to add in the search
     */
    public void updateCounterOnQuantityLine(Sign sign, Inventory chestShopInventory, ItemStack... extraItems) {
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

        int numTradedItemsInChest = inventoryService.getAmount(itemTradedByShop, chestShopInventory);

        for (ItemStack extraStack : extraItems) {
            if (!materialService.equals(extraStack, itemTradedByShop)) {
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

    public void updateCounterOnItemMoveEvent(ItemStack toAdd, InventoryHolder destinationHolder) {
        Block shopBlock = chestShopSign.getShopBlock(destinationHolder);
        Sign connectedSign = shopBlockService.getConnectedSign(shopBlock);

        updateCounterOnQuantityLine(connectedSign, destinationHolder.getInventory(), toAdd);
    }

    public void removeCounterFromQuantityLine(Sign sign) {
        int quantity;
        try {
            quantity = ChestShopSign.getQuantity(sign);
        } catch (IllegalFormatException invalidQuantity) {
            return;
        }

        sign.setLine(QUANTITY_LINE, Integer.toString(quantity));
        sign.update(true);
    }

    public String getQuantityLineWithCounter(int amount, ItemStack itemTransacted, Inventory chestShopInventory) {
        int numTransactionItemsInChest = inventoryService.getAmount(itemTransacted, chestShopInventory);

        return String.format(PRICE_LINE_WITH_COUNT, amount, numTransactionItemsInChest);
    }

    public ItemStack determineItemTradedByShop(Sign sign) {
        return determineItemTradedByShop(ChestShopSign.getItem(sign));
    }

    public ItemStack determineItemTradedByShop(String material) {
        return items.parse(material);
    }
}
