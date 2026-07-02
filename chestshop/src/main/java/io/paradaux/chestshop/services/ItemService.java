package io.paradaux.chestshop.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.plugins.ItemBridge;
import io.paradaux.chestshop.plugins.Nexo;
import io.paradaux.chestshop.utils.InventoryUtil;
import io.paradaux.chestshop.utils.MaterialUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.paradaux.chestshop.utils.MaterialUtil.MAXIMUM_SIGN_WIDTH;
import static io.paradaux.chestshop.utils.StringUtil.getMinecraftStringWidth;

/**
 * Resolves the item/sign strings ChestShop puts on signs. Replaces the
 * {@code ItemParseEvent} / {@code ItemStringQueryEvent} / {@code MaterialParseEvent} /
 * {@code SignValidationEvent} carriers and their resolver "listeners" with direct,
 * ordered service methods (PAR-282). The resolution order is unchanged:
 * <ul>
 *   <li>{@link #parse}: ItemBridge (if hooked) → configured alias → vanilla material
 *       (the alias step overrides an ItemBridge match, as before);</li>
 *   <li>{@link #queryString}: ItemBridge key (if hooked) → vanilla name → alias override;</li>
 *   <li>{@link #parseMaterial}: a single resolver.</li>
 * </ul>
 *
 * <p>Sign-format validation is pure and lives on {@code ChestShopSign.validateSign}.</p>
 *
 * <p>The optional ItemBridge integration (a softdepend) is only invoked once that plugin
 * is hooked ({@link #enableItemBridge()} is called from {@code Dependencies}), keeping the
 * {@code com.jojodmo.itembridge} classes off the call path when the plugin is absent.
 */
@Singleton
public class ItemService {

    private final ItemAliasModule aliases;
    private final ItemCodeService itemCodes;
    private final MaterialService materialService;
    private final InventoryUtil inventoryUtil;
    private volatile boolean itemBridgeEnabled = false;
    private volatile boolean nexoEnabled = false;

    @Inject
    public ItemService(ItemAliasModule aliases, ItemCodeService itemCodes, MaterialService materialService, InventoryUtil inventoryUtil) {
        this.aliases = aliases;
        this.itemCodes = itemCodes;
        this.materialService = materialService;
        this.inventoryUtil = inventoryUtil;
    }

    /** Mark the ItemBridge custom-item integration as available (called when the plugin hooks). */
    public void enableItemBridge() {
        this.itemBridgeEnabled = true;
    }

    /** Mark the Nexo custom-item integration as available + load nexo.yml (called when the plugin hooks). */
    public void enableNexo() {
        Nexo.init(itemCodes);
        this.nexoEnabled = true;
    }

    /** Reload the configurable item aliases (and Nexo's nexo.yml, if hooked) from disk (on {@code /chestshop reload}). */
    public void reloadAliases() {
        aliases.reload();
        if (nexoEnabled) {
            Nexo.reload();
        }
    }

    /** Parse a sign item string into an {@link ItemStack} (Nexo / ItemBridge / alias / vanilla material), or {@code null}. */
    public ItemStack parse(String itemString) {
        ItemStack item = nexoEnabled ? Nexo.parseItem(itemString) : null;
        if (item == null && itemBridgeEnabled) {
            item = ItemBridge.parseItem(itemString);
        }
        ItemStack alias = aliases.resolveItem(itemString); // a configured alias overrides a custom-item match
        if (alias != null) {
            item = alias;
        }
        if (item == null) {
            item = itemCodes.decode(itemString); // vanilla fallback
        }
        return item;
    }

    /** The sign string for an item (Nexo / ItemBridge key / vanilla name / configured alias), within {@code maxWidth}. */
    public String queryString(ItemStack item, int maxWidth) {
        String result = nexoEnabled ? Nexo.queryString(item, maxWidth) : null;
        if (result == null && itemBridgeEnabled) {
            result = ItemBridge.queryString(item, maxWidth);
        }
        if (result == null) {
            result = itemCodes.encode(item, maxWidth); // vanilla name
        }
        return aliases.applyAlias(result, maxWidth); // configured alias override
    }

    /** Resolve the material part of an item code (vanilla material lookup), or {@code null}. */
    public Material parseMaterial(String materialString, short data) {
        return materialService.getMaterial(materialString); // the legacy data value is ignored on modern materials
    }

    // ---- item display names (were the static ItemUtil helpers; PAR-282) ---------

    /** A comma-joined "count name" list for a set of stacks (used in trade/give messages). */
    public String getItemList(ItemStack[] items) {
        Map<ItemStack, Integer> itemCounts = inventoryUtil.getItemCounts(items);

        List<String> itemText = new ArrayList<>();
        for (Map.Entry<ItemStack, Integer> entry : itemCounts.entrySet()) {
            itemText.add(entry.getValue() + " " + getName(entry.getKey()));
        }

        return String.join(", ", itemText);
    }

    /** The item's full (untruncated) ChestShop name/code. */
    public String getName(ItemStack itemStack) {
        return getName(itemStack, 0);
    }

    /**
     * The item's ChestShop name/code, constrained to {@code maxWidth} pixels (0 = unlimited).
     * Throws {@link IllegalArgumentException} if a width-shortened code no longer round-trips
     * back to the same item.
     */
    public String getName(ItemStack itemStack, int maxWidth) {
        String code = queryString(itemStack, maxWidth);
        if (code != null) {
            if (maxWidth > 0) {
                int codeWidth = getMinecraftStringWidth(code);
                if (codeWidth > maxWidth) {
                    int exceeding = codeWidth - maxWidth;

                    int poundIndex = code.indexOf('#');
                    int colonIndex = code.indexOf(':');
                    String material = code;
                    String rest = "";
                    if (poundIndex > 0 && (colonIndex < 0 || poundIndex < colonIndex)) {
                        material = code.substring(0, poundIndex);
                        rest = code.substring(poundIndex);
                    } else if (colonIndex > 0 && (poundIndex < 0 || colonIndex < poundIndex)) {
                        material = code.substring(0, colonIndex);
                        rest = code.substring(colonIndex);
                    }
                    code = MaterialUtil.getShortenedName(material, getMinecraftStringWidth(material) - exceeding) + rest;
                }
            }

            ItemStack codeItem = parse(code);
            if (!materialService.equals(itemStack, codeItem)) {
                throw new IllegalArgumentException("Cannot generate code for item " + itemStack
                        + " with maximum length of " + maxWidth
                        + " (code " + code + " results in item " + codeItem + ")");
            }
        }
        return code;
    }

    /** The item's name as it appears on a sign (constrained to the sign width). */
    public String getSignName(ItemStack itemStack) {
        return getName(itemStack, MAXIMUM_SIGN_WIDTH);
    }
}
