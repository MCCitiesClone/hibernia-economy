package io.paradaux.chestshop.plugins;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.themoep.ShowItem.api.ShowItem;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.utils.InventoryUtil;
import io.paradaux.hibernia.framework.i18n.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.logging.Level;

/**
 * The optional <a href="https://www.spigotmc.org/resources/showitem.51835/">ShowItem</a>
 * integration: renders shop messages with the traded item as an inline hover/icon
 * component. Extracted out of {@code MaterialUtil} (was its {@code Show} inner class) so
 * it can inject {@link InventoryUtil} directly — which removes the former
 * {@code MaterialUtil ↔ InventoryUtil} construction cycle and its {@code Provider<>}
 * work-around — and sits with the other softdepend integrations.
 *
 * <p>{@link #initialize(Plugin)} is called from {@code Dependencies} when the ShowItem
 * plugin hooks; until then {@link #sendMessage} returns {@code false} so callers fall
 * back to a plain message.
 */
@Singleton
public class ShowItemHook {

    // Held as Plugin, not ShowItem: this class is Guice-managed, and Guice scans a
    // managed class's fields (calling getType()), which would eagerly load the
    // de.themoep.ShowItem type — a compileOnly softdepend absent at runtime unless the
    // plugin is installed. Keeping it Plugin-typed and casting to ShowItem only inside
    // sendMessage() (guarded by the null check) defers that load to when it's actually here.
    private static Plugin showItem = null;

    private final InventoryUtil inventoryUtil;

    @Inject
    public ShowItemHook(InventoryUtil inventoryUtil) {
        this.inventoryUtil = inventoryUtil;
    }

    /** Record the hooked ShowItem plugin (process-wide) once it's available. */
    public static void initialize(Plugin plugin) {
        showItem = plugin;
    }

    public boolean sendMessage(Message message, Player player, String key, ItemStack[] stock, Map<String, String> replacementMap, String... replacements) {
        return sendMessage(message, player, player.getName(), key, stock, replacementMap, replacements);
    }

    public boolean sendMessage(Message message, Player player, String playerName, String key, ItemStack[] stock, Map<String, String> replacementMap, String... replacements) {
        return sendMessage(message, player, playerName, key, true, stock, replacementMap, replacements);
    }

    public boolean sendMessage(Message message, Player player, String playerName, String key, boolean showPrefix, ItemStack[] stock, Map<String, String> replacementMap, String... replacements) {
        if (showItem == null) {
            return false;
        }

        TextComponent.Builder itemComponent = Component.text();
        for (Map.Entry<ItemStack, Integer> entry : inventoryUtil.getItemCounts(stock).entrySet()) {
            try {
                ItemStack item = entry.getKey();
                if (item == null || item.getType() == Material.AIR || entry.getValue() <= 0) {
                    continue;
                }
                if (entry.getValue() < item.getMaxStackSize()) {
                    item.setAmount(entry.getValue());
                }
                itemComponent.append(GsonComponentSerializer.gson().deserialize(((ShowItem) showItem).getItemConverter().createComponent(item, Level.FINE).toJsonString(player)));
            } catch (Exception e) {
                ChestShop.getPlugin().getLogger().log(Level.WARNING, "Error while trying to send message '" + key + "' to player " + player.getName() + ": " + e.getMessage());
                return false;
            }
        }

        // Render through the framework Message with the built item icon passed as the
        // {item} placeholder value: a ComponentLike renders inline, so the item (with its
        // hover) is embedded without MineDown's Replacer.
        Map<String, Object> values = ChestShop.values(showPrefix, replacementMap, replacements);
        values.put("item", itemComponent.build());
        Component component = message.component(key, values);
        if (player != null) {
            player.sendMessage(component);
            return true;
        } else if (playerName != null) {
            ChestShop.sendBungeeMessage(playerName, component);
            return true;
        }

        return true;
    }
}
