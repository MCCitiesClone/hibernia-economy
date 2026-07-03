package io.paradaux.chestshop.services;
import lombok.extern.slf4j.Slf4j;

import io.paradaux.chestshop.ChestShop;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import static io.paradaux.chestshop.utils.StringUtil.getMinecraftStringWidth;

/**
 * Owns the configurable item-code ↔ alias mapping (itemAliases.yml). The
 * {@link #onItemParse}/{@link #onItemStringQuery} resolvers are invoked directly by
 * {@link io.paradaux.chestshop.services.ItemService} (was a LOW/HIGH listener on the
 * item events), and {@link #reload} is called on {@code /chestshop reload}.
 *
 * @author Acrobot
 */
@Singleton
@Slf4j
public class ItemAliasModule {
    private YamlConfiguration configuration;
    /**
     * Map ChestShop item code -> alias
     */
    private BiMap<String, String> aliases;

    private final ItemCodeService itemCodes;

    @Inject
    public ItemAliasModule(ItemCodeService itemCodes) {
        this.itemCodes = itemCodes;
        load();
    }

    private void load() {
        File file = new File(ChestShop.getFolder(), "itemAliases.yml");

        configuration = YamlConfiguration.loadConfiguration(file);

        configuration.options().header(
                "This file specified optional aliases for certain item codes. (Use the full name from /iteminfo)"
                        + "\nPlease note that these aliases should fit on a sign for it to work properly!"
        );

        if (!file.exists()) {
            configuration.addDefault("Item String#3d", "My Cool Item");
            configuration.addDefault("Other Material#Eg", "Some other Item");

            try {
                configuration.options().copyDefaults(true);
                configuration.save(ChestShop.loadFile("itemAliases.yml"));
            } catch (IOException e) {
                log.error("Error while saving item aliases config", e);
            }
        }

        aliases = HashBiMap.create(configuration.getKeys(false).size());

        for (String key : configuration.getKeys(false)) {
            if (configuration.isString(key)) {
                aliases.put(key, configuration.getString(key));
            }
        }
    }

    /** Reload the alias map from disk (was the ChestShopReloadEvent handler). */
    public void reload() {
        load();
    }

    /**
     * Resolve a configured alias for {@code itemString} to an {@link ItemStack}, or
     * {@code null} if no alias matches. Called by {@link io.paradaux.chestshop.services.ItemService#parse}.
     */
    public ItemStack resolveItem(String itemString) {
        String code = aliases.inverse().get(itemString);
        if (code == null) {
            String[] typeParts = itemString.replaceAll("(?<!^)([A-Z1-9])", "_$1").toUpperCase(Locale.ROOT).split("[ _\\-]");
            int length = Short.MAX_VALUE;
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                if (entry.getValue().length() < length && entry.getValue().toUpperCase(Locale.ROOT).startsWith(itemString.toUpperCase(Locale.ROOT))) {
                    length = (short) entry.getValue().length();
                    code = entry.getKey();
                } else if (typeParts.length > 1) {
                    String[] nameParts = entry.getValue().toUpperCase(Locale.ROOT).split("[ _\\-]");
                    if (typeParts.length == nameParts.length) {
                        boolean matched = true;
                        for (int i = 0; i < nameParts.length; i++) {
                            if (!nameParts[i].startsWith(typeParts[i])) {
                                matched = false;
                                break;
                            }
                        }
                        if (matched) {
                            code = entry.getKey();
                            break;
                        }
                    }
                }
            }
        }
        return code != null ? itemCodes.decode(code) : null;
    }

    /**
     * Apply a configured alias to {@code itemString}, returning the alias if one is
     * configured and fits {@code maxWidth}, otherwise the input unchanged. Called by
     * {@link io.paradaux.chestshop.services.ItemService#queryString}.
     */
    public String applyAlias(String itemString, int maxWidth) {
        if (itemString == null) {
            return null;
        }
        String newCode;
        if (aliases.containsKey(itemString)) {
            newCode = aliases.get(itemString);
        } else if (!itemString.contains("#")) {
            newCode = aliases.get(itemString.toLowerCase(Locale.ROOT));
        } else {
            String[] parts = itemString.split("#", 2);
            String lowercaseCode = parts[0].toLowerCase(Locale.ROOT) + "#" + parts[1];
            newCode = aliases.containsKey(lowercaseCode) ? aliases.get(lowercaseCode) : null;
        }

        if (newCode == null) {
            return itemString;
        }
        if (maxWidth > 0) {
            int width = getMinecraftStringWidth(newCode);
            if (width > maxWidth) {
                log.warn("Can't use configured alias " + newCode + " as it's width (" + width + ") was wider than the allowed max width of " + maxWidth);
                return itemString;
            }
        }
        return newCode;
    }
}
