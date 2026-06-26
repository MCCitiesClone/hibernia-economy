package io.paradaux.chestshop.listeners.posttransaction;

import io.paradaux.chestshop.utils.InventoryUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.ShopDestroyedEvent;
import io.paradaux.chestshop.events.TransactionEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Removes a shop whose container has run dry after a buy. Formerly a
 * {@code @HIGHEST(ignoreCancelled=true)} {@link TransactionEvent} listener; now
 * invoked directly by {@link io.paradaux.chestshop.services.TransactionService#process}
 * at the equivalent point in the post-transaction order.
 *
 * @author Acrobot
 */
public class EmptyShopDeleter {

    public static void onTransaction(TransactionEvent event) {
        if (event.getTransactionType() != TransactionEvent.TransactionType.BUY) {
            return;
        }

        Sign sign = event.getSign();

        if (ChestShopSign.isAdminShop(sign)) {
            return;
        }

        Inventory ownerInventory = event.getOwnerInventory();

        if (!shopShouldBeRemoved(ownerInventory, event.getStock())) {
            return;
        }

        if (!isInRemoveWorld(sign)) {
            return;
        }

        Container connectedContainer = uBlock.findConnectedContainer(sign);

        ShopDestroyedEvent destroyedEvent = new ShopDestroyedEvent(null, event.getSign(), connectedContainer);
        ChestShop.shops().onDestroyed(destroyedEvent);

        Material signType = sign.getType();
        sign.getBlock().setType(Material.AIR);

        if (Properties.REMOVE_EMPTY_CHESTS && !ChestShopSign.isAdminShop(ownerInventory) && InventoryUtil.isEmpty(ownerInventory)) {
            if (connectedContainer != null) {
                connectedContainer.getBlock().setType(Material.AIR);
            }
        } else {
            if (!signType.isItem()) {
                try {
                    signType = Material.valueOf(signType.name().replace("WALL_", ""));
                } catch (IllegalArgumentException ignored) {}
            }
            if (signType.isItem()) {
                ownerInventory.addItem(new ItemStack(signType, 1));
            } else {
                ChestShop.getBukkitLogger().warning("Unable to get item for sign " + signType + " to add to removed shop's container!");
            }
        }
    }

    private static boolean shopShouldBeRemoved(Inventory inventory, ItemStack[] stock) {
        if (Properties.REMOVE_EMPTY_SHOPS) {
            if (Properties.ALLOW_PARTIAL_TRANSACTIONS) {
                for (ItemStack itemStack : stock) {
                    if (inventory.containsAtLeast(itemStack, 1)) {
                        return false;
                    }
                }
                return true;
            } else if (!InventoryUtil.hasItems(stock, inventory)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInRemoveWorld(Sign sign) {
        return Properties.REMOVE_EMPTY_WORLDS.isEmpty() || Properties.REMOVE_EMPTY_WORLDS.contains(sign.getWorld().getName());
    }
}
