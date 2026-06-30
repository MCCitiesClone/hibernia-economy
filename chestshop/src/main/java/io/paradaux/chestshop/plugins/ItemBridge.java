package io.paradaux.chestshop.plugins;

import io.paradaux.chestshop.ChestShop;
import com.jojodmo.itembridge.ItemBridgeKey;
import org.bukkit.inventory.ItemStack;

import static io.paradaux.chestshop.utils.StringUtil.getMinecraftStringWidth;

/**
 * Support for the <a href="https://www.spigotmc.org/resources/77080/">ItemBridge plugin</a> to use their strings for
 * custom items directly on ChestShop signs. These methods are invoked directly (and only
 * when the ItemBridge plugin is hooked) by {@link io.paradaux.chestshop.services.ItemService},
 * which keeps the {@code com.jojodmo.itembridge} classes off the call path when the plugin
 * is absent — exactly as registering this listener only on hook used to.
 *
 * @author Phoenix616
 */
public class ItemBridge {

    /** Resolve a custom-item string to an {@link ItemStack}, or {@code null} if ItemBridge doesn't know it. */
    public static ItemStack parseItem(String itemString) {
        return com.jojodmo.itembridge.ItemBridge.getItemStack(itemString);
    }

    /**
     * The ItemBridge sign string for an item, or {@code null} if the item has no non-vanilla
     * ItemBridge key or that key would be too wide for {@code maxWidth}.
     */
    public static String queryString(ItemStack item, int maxWidth) {
        ItemBridgeKey key = com.jojodmo.itembridge.ItemBridge.getItemKey(item);
        // A "minecraft" namespace means a vanilla item — ignore it and let our own logic name it.
        if (key == null || "minecraft".equalsIgnoreCase(key.getNamespace())) {
            return null;
        }
        String code = key.toString();
        if (maxWidth > 0) {
            int width = getMinecraftStringWidth(code);
            if (width > maxWidth) {
                ChestShop.logDebug("Can't use ItemBridge alias " + code + " as it's width (" + width + ") was wider than the allowed max width of " + maxWidth);
                return null;
            }
        }
        return code;
    }
}
