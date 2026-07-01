package io.paradaux.chestshop.utils;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConstructor;
import org.bukkit.configuration.file.YamlRepresenter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.paradaux.chestshop.utils.StringUtil.getMinecraftCharWidth;
import static io.paradaux.chestshop.utils.StringUtil.getMinecraftStringWidth;

/**
 * @author Acrobot
 */
@Singleton
public class MaterialUtil {
    public static final Pattern DURABILITY = Pattern.compile(":\\d+");
    public static final Pattern METADATA = Pattern.compile("#[0-9a-zA-Z]+");

    @Deprecated
    public static final boolean LONG_NAME = true;
    @Deprecated
    public static final boolean SHORT_NAME = false;
    /**
     * @deprecated Use {@link MaterialUtil#MAXIMUM_SIGN_WIDTH}
     */
    @Deprecated
    public static final short MAXIMUM_SIGN_LETTERS = 15;
    // 15 dashes fit on one sign line with the default resource pack:
    public static final int MAXIMUM_SIGN_WIDTH = (short) getMinecraftStringWidth("---------------");

    private static final Map<String, String> ABBREVIATIONS = StringUtil.map(
            "Egg", "Eg",
            "Spawn", "Spaw",
            "Pottery", "Pot",
            "Heartbreak", "Heartbr",
            "Sherd", "Sher"
    );

    private static final Map<String, String> UNIDIRECTIONAL_ABBREVIATIONS = StringUtil.map(
            "Chestplate", "Chestplt",
            "Chestplt", "Chstplt",
            "Chstplt", "Chstpl",
            "Endermite", "Endmite",
            "Endmite", "Endmit",
            "Wayfinder", "Wayfndr",
            "Wayfndr", "Wf",
            "Heartbr", "Hrtbr",
            "Hrtbr", "Hrtb"
    );

    private final SimpleCache<String, Material> materialCache;

    private static final Yaml YAML = new Yaml(new YamlBukkitConstructor(), new YamlRepresenter(), new DumperOptions());

    private final ChestShopConfiguration config;

    @Inject
    public MaterialUtil(ChestShopConfiguration config) {
        this.config = config;
        this.materialCache = new SimpleCache<>(config.getCacheSize());
    }

    private static class YamlBukkitConstructor extends YamlConstructor {
        public YamlBukkitConstructor() {
            this.yamlConstructors.put(new Tag(Tag.PREFIX + "org.bukkit.inventory.ItemStack"), yamlConstructors.get(Tag.MAP));
        }
    }

    /**
     * Checks if the itemStack is empty or null
     *
     * @param item Item to check
     * @return Is the itemStack empty?
     */
    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    /**
     * Checks if the itemStacks are equal, ignoring their amount
     *
     * @param one first itemStack
     * @param two second itemStack
     * @return Are they equal?
     */
    public boolean equals(ItemStack one, ItemStack two) {
        if (one == null || two == null) {
            return one == two;
        }
        if (one.isSimilar(two)) {
            return true;
        }

        // Additional checks as serialisation and de-serialisation might lead to different item meta
        // This would only be done if the items share the same item meta type so it shouldn't be too inefficient
        // Special check for books as their pages might change when serialising (See SPIGOT-3206 and ChestShop#250)
        // Special check for explorer maps/every item with a localised name (See SPIGOT-4672)
        // Special check for legacy spawn eggs (See ChestShop#264)
        if (one.getType() != two.getType()
                || one.getDurability() != two.getDurability()
                || (one.hasItemMeta() && two.hasItemMeta() && one.getItemMeta().getClass() != two.getItemMeta().getClass())) {
            return false;
        }
        if (!one.hasItemMeta() && !two.hasItemMeta()) {
            return true;
        }
        ItemMeta oneMeta = one.getItemMeta();
        ItemMeta twoMeta = two.getItemMeta();
        // return true if both are null or same, false if only one is null
        if (oneMeta == twoMeta || oneMeta == null || twoMeta == null) {
            return oneMeta == twoMeta;
        }

        Map<String, Object> oneSerMeta = new HashMap<>(oneMeta.serialize());
        Map<String, Object> twoSerMeta = new HashMap<>(twoMeta.serialize());

        removeExcludedKeys(oneSerMeta);
        removeExcludedKeys(twoSerMeta);

        if (oneSerMeta.equals(twoSerMeta)) {
            return true;
        }

        // Try to use same parsing as the YAML dumper in the ItemDatabase when generating the code as the last resort
        ItemStack oneCloned = one.clone();
        oneCloned.setAmount(1);

        ItemStack twoCloned = two.clone();
        twoCloned.setAmount(1);

        ItemStack oneDumped = YAML.loadAs(YAML.dump(oneCloned), ItemStack.class);
        if (oneDumped.isSimilar(twoCloned)) {
            return true;
        }

        ItemMeta oneDumpedMeta = oneDumped.getItemMeta();
        if (oneDumpedMeta != null && oneDumpedMeta.serialize().equals(twoSerMeta)) {
            return true;
        }

        ItemStack twoDumped = YAML.loadAs(YAML.dump(twoCloned), ItemStack.class);
        if (oneDumped.isSimilar(twoDumped)) {
            return true;
        }

        ItemMeta twoDumpedMeta = twoDumped.getItemMeta();
        if (oneDumpedMeta != null && twoDumpedMeta != null) {
            Map<String, Object> oneSerDumpedMeta = new HashMap<>(oneDumpedMeta.serialize());
            Map<String, Object> twoSerDumpedMeta = new HashMap<>(twoDumpedMeta.serialize());

            removeExcludedKeys(oneSerDumpedMeta);
            removeExcludedKeys(twoSerDumpedMeta);

            if (oneSerDumpedMeta.equals(twoSerDumpedMeta)) {
                return true;
            }
        }

        // return true if both are null or same, false otherwise
        return oneDumpedMeta == twoDumpedMeta;
    }

    /**
     * Remove all keys included in the {@code EXCLUDED_ITEM_ATTRIBUTES} config option from a serialized
     * meta map
     * @param map The serialized item data to modify
     */
    private void removeExcludedKeys(Map<String, Object> map) {
        map.keySet().removeAll(config.getExcludedItemAttributes());
    }

    /**
     * Gives you a Material from a String (doesn't have to be fully typed in)
     *
     * @param name Name of the material
     * @return Material found
     */
    public Material getMaterial(String name) {
        String replacedName = name;
        // revert unidirectional abbreviations
        List<Map.Entry<String, String>> abbreviations = new ArrayList<>(UNIDIRECTIONAL_ABBREVIATIONS.entrySet());
        for (int i = abbreviations.size() - 1; i >= 0; i--) {
            Map.Entry<String, String> entry = abbreviations.get(i);
            replacedName = replacedName.replaceAll(entry.getValue() + "(_|$|[A-Z\\d])", entry.getKey() + "$1");
        }

        String formatted = name.replaceAll("(?<!^)(?>\\s?)([A-Z1-9])", "_$1").replace(' ', '_').toUpperCase(Locale.ROOT);

        Material material = materialCache.get(formatted);
        if (material != null) {
            return material;
        }

        material = Material.matchMaterial(name);

        if (material != null) {
            materialCache.put(formatted, material);
            return material;
        }

        material = new EnumParser<Material>().parse(replacedName, Material.values());
        if (material != null) {
            materialCache.put(formatted, material);
        }

        return material;
    }

    // The item ↔ sign-code naming (getName/getItem/getSignName/getItemList/getMetadata)
    // and the #code Metadata facade moved to ItemCodeService.encode/decode (PAR-282) — the
    // canonical conversion is DB-backed, so it belongs in the service, not this pure util.
    // The pure helpers it composes (getMaterial, getDurability, parseMetadata, hasCustomData,
    // getShortenedName, equals, …) stay here.

    /**
     * Check whether the provided ItemStack has custom data (in the past called "ItemMeta"). This will ignore
     * the durability of an item.
     *
     * @param itemStack The ItemStack to check
     * @return Whether the item has custom data
     */
    public static boolean hasCustomData(ItemStack itemStack) {
        if (!itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta instanceof Damageable) {
            if (!((Damageable) itemMeta).hasDamage()) {
                return true;
            }
        }
        Map<String, Object> data = itemMeta.serialize();
        // if the data map contains more than the metadata type and the damage
        // then the item does indeed have custom data set
        return data.size() > 2;
    }

    /**
     * Get an item name shortened to a max length that is still reversable by {@link #getMaterial(String)}
     *
     * @param itemName  The name of the item
     * @param maxWidth  The max width
     * @return The name shortened to the max length
     */
    public static String getShortenedName(String itemName, int maxWidth) {
        // Restore spaces in string that might be already be shortened
        String name = itemName.replaceAll("([a-z])([A-Z1-9])", "$1 $2");
        name = StringUtil.capitalizeFirstLetter(name.replace('_', ' '), ' ');
        int width = getMinecraftStringWidth(name);
        if (width <= maxWidth) {
            return name;
        }
        String[] itemParts = name.split("[ \\-]");
        String noSpaceName = String.join("", itemParts);
        width = getMinecraftStringWidth(noSpaceName);
        if (width <= maxWidth) {
            return noSpaceName;
        }

        // Abbreviate some terms manually
        for (Map.Entry<String, String> entry : ABBREVIATIONS.entrySet()) {
            name = name.replaceAll(entry.getKey() + "( |$)", entry.getValue() + "$1");
            itemParts = name.split("[ \\-]");
            noSpaceName = String.join("", itemParts);
            width = getMinecraftStringWidth(noSpaceName);
            if (width <= maxWidth) {
                return noSpaceName;
            }
        }

        // Apply unidirectional abbreviations if it still doesn't work
        for (Map.Entry<String, String> entry : UNIDIRECTIONAL_ABBREVIATIONS.entrySet()) {
            name = name.replaceAll(entry.getKey() + "( |$)", entry.getValue() + "$1");
            itemParts = name.split("[ \\-]");
            noSpaceName = String.join("", itemParts);
            width = getMinecraftStringWidth(noSpaceName);
            if (width <= maxWidth) {
                return noSpaceName;
            }
        }

        int exceeding = width - maxWidth;
        int shortestIndex = 0;
        int longestIndex = 0;
        for (int i = 0; i < itemParts.length; i++) {
            if (getMinecraftStringWidth(itemParts[longestIndex]) < getMinecraftStringWidth(itemParts[i])) {
                longestIndex = i;
            }
            if (getMinecraftStringWidth(itemParts[shortestIndex]) > getMinecraftStringWidth(itemParts[i])) {
                shortestIndex = i;
            }
        }
        int shortestWidth = getMinecraftStringWidth(itemParts[shortestIndex]);
        int longestWidth = getMinecraftStringWidth(itemParts[longestIndex]);
        int remove = longestWidth - shortestWidth;
        while (remove > 0 && exceeding > 0) {
            int endWidth = getMinecraftCharWidth(itemParts[longestIndex].charAt(itemParts[longestIndex].length() - 1));
            itemParts[longestIndex] = itemParts[longestIndex].substring(0, itemParts[longestIndex].length() - 1);
            remove -= endWidth;
            exceeding -= endWidth;
        }

        for (int i = itemParts.length - 1; i >= 0 && exceeding > 0; i--) {
            int partWidth = getMinecraftStringWidth(itemParts[i]);

            if (partWidth > shortestWidth) {
                remove = partWidth - shortestWidth;
            }

            if (remove > exceeding) {
                remove = exceeding;
            }

            while (remove > 0) {
                int endWidth = getMinecraftCharWidth(itemParts[i].charAt(itemParts[i].length() - 1));
                itemParts[i] = itemParts[i].substring(0, itemParts[i].length() - 1);
                remove -= endWidth;
                exceeding -= endWidth;
            }
        }

        while (exceeding > 0) {
            for (int i = itemParts.length - 1; i >= 0 && exceeding > 0; i--) {
                int endWidth = getMinecraftCharWidth(itemParts[i].charAt(itemParts[i].length() - 1));
                itemParts[i] = itemParts[i].substring(0, itemParts[i].length() - 1);
                exceeding -= endWidth;
            }
        }
        return String.join("", itemParts);
    }

    /**
     * Returns the durability from a string
     *
     * @param itemName Item name
     * @return Durability found
     */
    public static Integer getDurability(String itemName) {
        Matcher m = DURABILITY.matcher(itemName);

        if (!m.find()) {
            return null;
        }

        String data = m.group();

        if (data == null || data.isEmpty()) {
            return null;
        }

        data = data.substring(1);

        try {
            return Integer.parseInt(data);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Extract the #code metadata token from an item string (the part after '#'), or
     * {@code null} if none. Pure parsing — {@link io.paradaux.chestshop.services.ItemCodeService#decode}
     * resolves the token to an {@link ItemMeta}.
     *
     * @param itemName Item name
     * @return Metadata token found
     */
    public static String parseMetadata(String itemName) {
        Matcher m = METADATA.matcher(itemName);

        if (!m.find()) {
            return null;
        }

        return m.group().substring(1);
    }

    private static class EnumParser<E extends Enum<E>> {
        private E parse(String name, E[] values) {
            String formatted = name.replaceAll("(?<!^)(?>\\s?)([A-Z1-9])", "_$1").toUpperCase(Locale.ROOT).replace(' ', '_');
            try {
                return E.valueOf(values[0].getDeclaringClass(), formatted);
            } catch (IllegalArgumentException exception) {
                List<E> possibleEnums = new ArrayList<>();
                String[] typeParts = formatted.split("_");
                int length = Short.MAX_VALUE;
                for (E e : values) {
                    String enumName = e.name();
                    if (enumName.length() < length && enumName.startsWith(formatted)) {
                        length = enumName.length();
                        possibleEnums.add(e);
                    } else if (typeParts.length > 1) {
                        String[] nameParts = enumName.split("_");
                        if (typeParts.length == nameParts.length) {
                            boolean matched = true;
                            for (int i = 0; i < nameParts.length; i++) {
                                if (!nameParts[i].startsWith(typeParts[i])) {
                                    matched = false;
                                    break;
                                }
                            }
                            if (matched) {
                                possibleEnums.add(e);
                            }
                        }
                    }
                }

                if (possibleEnums.size() == 1) {
                    return possibleEnums.get(0);
                } else if (possibleEnums.size() > 1) {
                    int formattedLength = formatted.length();
                    int closestDeviation = Short.MAX_VALUE;
                    E closestEnum = null;
                    for (E possibleEnum : possibleEnums) {
                        int deviation = possibleEnum.name().length() - formattedLength;
                        if (deviation < closestDeviation) {
                            closestDeviation = deviation;
                            closestEnum = possibleEnum;
                        }
                    }
                    return closestEnum;
                }
                return null;
            }
        }
    }

}
