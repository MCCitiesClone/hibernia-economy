package io.paradaux.chestshop.plugins;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.ItemParseEvent;
import io.paradaux.chestshop.events.ItemStringQueryEvent;
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

    public static void onItemParse(ItemParseEvent event) {
        if (event.getItem() == null) {
            ItemStack item = com.jojodmo.itembridge.ItemBridge.getItemStack(event.getItemString());
            if (item != null) {
                event.setItem(item);
            }
        }
    }

    public static void onItemStringQuery(ItemStringQueryEvent event) {
        ItemBridgeKey key = com.jojodmo.itembridge.ItemBridge.getItemKey(event.getItem());
        // If namespace is "minecraft" then we ignore it and use our own logic
        if (key != null && !"minecraft".equalsIgnoreCase(key.getNamespace())) {
            String code = key.toString();
            // Make sure the ItemBridge string is not too long as we can't parse shortened ones
            if (event.getMaxWidth() > 0) {
                int width = getMinecraftStringWidth(code);
                if (width > event.getMaxWidth()) {
                    ChestShop.logDebug("Can't use ItemBridge alias " + code + " as it's width (" + width + ") was wider than the allowed max width of " + event.getMaxWidth());
                    return;
                }
            }
            event.setItemString(key.toString());
        }
    }
}
