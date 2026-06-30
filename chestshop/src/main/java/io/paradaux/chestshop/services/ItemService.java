package io.paradaux.chestshop.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.listeners.modules.ItemAliasModule;
import io.paradaux.chestshop.plugins.ItemBridge;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.InventoryUtil;
import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.utils.StringUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *   <li>{@link #parseMaterial} / {@link #validateSign}: a single resolver each.</li>
 * </ul>
 *
 * <p>The optional ItemBridge integration (a softdepend) is only invoked once that plugin
 * is hooked ({@link #enableItemBridge()} is called from {@code Dependencies}), keeping the
 * {@code com.jojodmo.itembridge} classes off the call path when the plugin is absent.
 */
@Singleton
public class ItemService {

    /** Constant "name:id" disambiguation suffix (duplicate/too-long names) — compiled once. */
    private static final Pattern NAME_WITH_ID = Pattern.compile("^(.+):[A-Za-z0-9]+$");
    // The valid-playername regex comes from config and can change on reload, so cache it
    // by its source string rather than recompiling on every sign validation.
    private static volatile String cachedPlayernameRegexp;
    private static volatile Pattern cachedPlayernamePattern;

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
        ItemStack item = itemBridgeEnabled ? ItemBridge.parseItem(itemString) : null;
        ItemStack alias = aliases.resolveItem(itemString); // a configured alias overrides an ItemBridge match
        if (alias != null) {
            item = alias;
        }
        if (item == null) {
            item = MaterialUtil.getItem(itemString); // vanilla fallback
        }
        return item;
    }

    /** The sign string for an item (ItemBridge key / vanilla name / configured alias), within {@code maxWidth}. */
    public String queryString(ItemStack item, int maxWidth) {
        String result = itemBridgeEnabled ? ItemBridge.queryString(item, maxWidth) : null;
        if (result == null) {
            result = MaterialUtil.getName(item, maxWidth); // vanilla name
        }
        return aliases.applyAlias(result, maxWidth); // configured alias override
    }

    /** Resolve the material part of an item code (vanilla material lookup), or {@code null}. */
    public Material parseMaterial(String materialString, short data) {
        return MaterialUtil.getMaterial(materialString); // the legacy data value is ignored on modern materials
    }

    // ---- item display names (were the static ItemUtil helpers; PAR-282) ---------

    /** A comma-joined "count name" list for a set of stacks (used in trade/give messages). */
    public String getItemList(ItemStack[] items) {
        Map<ItemStack, Integer> itemCounts = InventoryUtil.getItemCounts(items);

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
            if (!MaterialUtil.equals(itemStack, codeItem)) {
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

    /** Whether the given sign lines form a valid ChestShop sign. */
    public boolean validateSign(String[] lines) {
        String ownerName = ChestShopSign.getOwner(lines);

        // Validate the owner as a player name unless it is blank (auto-filled), an admin
        // shop, or a business token (B:<base36 id> / legacy b:<FirmName>) resolved elsewhere.
        if (!ChestShopSign.isAdminShop(ownerName) && !ownerName.isEmpty()
                && !ownerName.regionMatches(true, 0, "B:", 0, 2)) {
            Matcher withId = NAME_WITH_ID.matcher(ownerName);
            if (withId.matches()) {
                // The name carries a disambiguation id — validate the part before the last ':'.
                ownerName = withId.group(1);
            }
            if (!validNamePattern().matcher(ownerName).matches()) {
                return false;
            }
        }

        // The last three lines must each match one of the configured shop-sign patterns.
        for (int i = 0; i < 3; i++) {
            boolean matches = false;
            for (Pattern pattern : ChestShopSign.SHOP_SIGN_PATTERN[i]) {
                if (pattern.matcher(StringUtil.strip(StringUtil.stripColourCodes(lines[i + 1]))).matches()) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return false;
            }
        }

        // A valid prepared sign has at most one ':' in the price line.
        String priceLine = ChestShopSign.getPrice(lines);
        return priceLine.indexOf(':') == priceLine.lastIndexOf(':');
    }

    private static Pattern validNamePattern() {
        String regexp = Properties.VALID_PLAYERNAME_REGEXP;
        if (!regexp.equals(cachedPlayernameRegexp)) {
            cachedPlayernamePattern = Pattern.compile(regexp);
            cachedPlayernameRegexp = regexp;
        }
        return cachedPlayernamePattern;
    }
}
