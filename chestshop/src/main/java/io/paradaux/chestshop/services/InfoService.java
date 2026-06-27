package io.paradaux.chestshop.services;

import com.google.inject.Singleton;
import io.paradaux.chestshop.events.ItemInfoEvent;
import io.paradaux.chestshop.events.ShopInfoEvent;
import io.paradaux.chestshop.listeners.ItemInfoListener;
import io.paradaux.chestshop.listeners.ShopInfoListener;
import io.paradaux.chestshop.listeners.iteminfo.ExtendedItemInfoListener;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Owns the read-only {@code /shopinfo} and {@code /iteminfo} output, replacing the
 * {@code ShopInfoEvent} / {@code ItemInfoEvent} bus with direct, ordered calls.
 *
 * <p>{@link #collectItemInfo} runs the former contributor handlers in their exact firing
 * order: the basic {@code ItemInfoListener} lines (@NORMAL), then the richer
 * {@code ExtendedItemInfoListener} lines — its @NORMAL handlers, then its @HIGH map/potion
 * handlers, which intentionally override the basic versions by message key (the
 * accumulating {@link java.util.LinkedHashMap} keeps the original line position).
 */
@Singleton
public class InfoService {

    /** Render the {@code /shopinfo} (or middle-click) output for a shop sign. */
    public void showShopInfo(Player sender, Sign sign) {
        ShopInfoListener.showShopInfo(new ShopInfoEvent(sender, sign));
    }

    /** Accumulate the {@code /iteminfo} lines for an item, in the former handler order. */
    public ItemInfoEvent collectItemInfo(CommandSender sender, ItemStack item) {
        ItemInfoEvent event = new ItemInfoEvent(sender, item);
        // ItemInfoListener — basic info lines (were @NORMAL), in declaration order.
        ItemInfoListener.addRepairCost(event);
        ItemInfoListener.addEnchantment(event);
        ItemInfoListener.addLeatherColor(event);
        ItemInfoListener.addRecipes(event);
        ItemInfoListener.addTropicalFishInfo(event);
        ItemInfoListener.addMapInfo(event);
        ItemInfoListener.addPotionInfo(event);
        ItemInfoListener.addBookInfo(event);
        ItemInfoListener.addLoreInfo(event);
        // ExtendedItemInfoListener — @NORMAL type-specific lines, then the @HIGH map/potion
        // handlers that override the basic versions above (same keys).
        ExtendedItemInfoListener.addCrossBowInfo(event);
        ExtendedItemInfoListener.addAxolotlInfo(event);
        ExtendedItemInfoListener.addBundleInfo(event);
        ExtendedItemInfoListener.addArmorInfo(event);
        ExtendedItemInfoListener.addMapInfo(event);
        ExtendedItemInfoListener.addPotionInfo(event);
        return event;
    }
}
