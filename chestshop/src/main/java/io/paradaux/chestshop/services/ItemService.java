package io.paradaux.chestshop.services;

import io.paradaux.chestshop.integration.nexo.Nexo;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves the item/sign strings ChestShop puts on signs. Replaces the
 * {@code ItemParseEvent} / {@code ItemStringQueryEvent} / {@code MaterialParseEvent} /
 * {@code SignValidationEvent} carriers and their resolver "listeners" with direct,
 * ordered service methods (PAR-282). The resolution order is unchanged:
 * <ul>
 *   <li>{@link #parse}: Nexo (if hooked) → configured alias → vanilla material
 *       (the alias step overrides a custom-item match, as before);</li>
 *   <li>{@link #queryString}: Nexo key (if hooked) → vanilla name → alias override;</li>
 *   <li>{@link #parseMaterial}: a single resolver.</li>
 * </ul>
 *
 * <p>Sign-format validation is pure and lives on {@code ChestShopSign.validateSign}.</p>
 *
 * <p>The optional Nexo custom-item integration (a softdepend) is only invoked once that
 * plugin is hooked ({@link #enableNexo()} is called from {@code NexoIntegration}), keeping the
 * {@code com.nexomc.nexo} classes off the call path when the plugin is absent.
 */
public interface ItemService {

    /** Mark the Nexo custom-item integration as available + load nexo.yml (called when the plugin hooks). */
    void enableNexo();

    /** Reload the configurable item aliases (and Nexo's nexo.yml, if hooked) from disk (on {@code /chestshop reload}). */
    void reloadAliases();

    /** Parse a sign item string into an {@link ItemStack} (Nexo / alias / vanilla material), or {@code null}. */
    ItemStack parse(String itemString);

    /** The sign string for an item (Nexo / vanilla name / configured alias), within {@code maxWidth}. */
    String queryString(ItemStack item, int maxWidth);

    /** Resolve the material part of an item code (vanilla material lookup), or {@code null}. */
    Material parseMaterial(String materialString, short data);

    /** A comma-joined "count name" list for a set of stacks (used in trade/give messages). */
    String getItemList(ItemStack[] items);

    /** The item's full (untruncated) ChestShop name/code. */
    String getName(ItemStack itemStack);

    /**
     * The item's ChestShop name/code, constrained to {@code maxWidth} pixels (0 = unlimited).
     * Throws {@link IllegalArgumentException} if a width-shortened code no longer round-trips
     * back to the same item.
     */
    String getName(ItemStack itemStack, int maxWidth);

    /** The item's name as it appears on a sign (constrained to the sign width). */
    String getSignName(ItemStack itemStack);
}
