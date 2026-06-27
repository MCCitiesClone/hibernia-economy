package io.paradaux.chestshop.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.events.ItemParseEvent;
import io.paradaux.chestshop.events.ItemStringQueryEvent;
import io.paradaux.chestshop.events.MaterialParseEvent;
import io.paradaux.chestshop.events.SignValidationEvent;
import io.paradaux.chestshop.listeners.SignParseListener;
import io.paradaux.chestshop.listeners.item.ItemStringListener;
import io.paradaux.chestshop.listeners.modules.ItemAliasModule;
import io.paradaux.chestshop.plugins.ItemBridge;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves the item/sign strings ChestShop puts on signs, replacing the
 * {@code ItemParseEvent} / {@code ItemStringQueryEvent} / {@code MaterialParseEvent} /
 * {@code SignValidationEvent} event bus with direct, ordered calls. The former
 * priority-ordered listeners now run as explicit steps in the exact same order:
 * <ul>
 *   <li>{@link #parse}: ItemBridge (custom-item plugin) → item aliases → vanilla material
 *       (LOW ItemBridge, LOW alias, NORMAL fallback — ItemBridge registered first);</li>
 *   <li>{@link #queryString}: ItemBridge → vanilla name → item aliases
 *       (LOW → NORMAL → HIGH);</li>
 *   <li>{@link #parseMaterial} and {@link #validateSign}: a single resolver each.</li>
 * </ul>
 *
 * <p>The optional ItemBridge integration (a softdepend) is only invoked once that plugin
 * is hooked ({@link #enableItemBridge()} is called from {@code Dependencies}); this keeps
 * the {@code com.jojodmo.itembridge} classes off the call path when the plugin is absent,
 * exactly as the old "only register the listener when the plugin loads" behaviour did.
 */
@Singleton
public class ItemService {

    private final ItemAliasModule aliases;
    private volatile boolean itemBridgeEnabled = false;

    @Inject
    public ItemService(ItemAliasModule aliases) {
        this.aliases = aliases;
    }

    /** Mark the ItemBridge custom-item integration as available (called when the plugin hooks). */
    public void enableItemBridge() {
        this.itemBridgeEnabled = true;
    }

    /** Reload the configurable item aliases from disk (on {@code /chestshop reload}). */
    public void reloadAliases() {
        aliases.reload();
    }

    /** Parse a sign item string into an {@link ItemStack} (ItemBridge / alias / vanilla material), or {@code null}. */
    public ItemStack parse(String itemString) {
        ItemParseEvent event = new ItemParseEvent(itemString);
        if (itemBridgeEnabled) {
            ItemBridge.onItemParse(event);
        }
        aliases.onItemParse(event);
        SignParseListener.onItemParse(event);
        return event.getItem();
    }

    /** The sign string for an item (ItemBridge key / vanilla name / configured alias), within {@code maxWidth}. */
    public String queryString(ItemStack item, int maxWidth) {
        ItemStringQueryEvent event = new ItemStringQueryEvent(item, maxWidth);
        if (itemBridgeEnabled) {
            ItemBridge.onItemStringQuery(event);
        }
        ItemStringListener.calculateItemString(event);
        aliases.onItemStringQuery(event);
        return event.getItemString();
    }

    /** Resolve the material part of an item code (vanilla material lookup), or {@code null}. */
    public Material parseMaterial(String materialString, short data) {
        MaterialParseEvent event = new MaterialParseEvent(materialString, data);
        SignParseListener.onMaterialParse(event);
        return event.getMaterial();
    }

    /** Whether the given sign lines form a valid ChestShop sign. */
    public boolean validateSign(String[] lines) {
        SignValidationEvent event = new SignValidationEvent(lines);
        SignParseListener.onSignValidation(event);
        return event.isValid();
    }
}
