package io.paradaux.chestshop.configuration;

import io.paradaux.chestshop.ChestShop;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * ChestShop's message catalogue. Each constant is a thin handle to a key in
 * {@code messages.properties}, rendered through the HiberniaFramework
 * {@link io.paradaux.hibernia.framework.i18n.Message} bean (MiniMessage formatting,
 * {@code {placeholder}} substitution) — the same i18n stack Treasury/Business use.
 *
 * <p>This replaced the former de.themoep MineDown + LanguageManager pipeline: the
 * fork doesn't need multi-language support, and removing MineDown removes a whole
 * class of Adventure-version incompatibility bugs (a MineDown internal type
 * implemented a now-sealed Adventure interface). The per-language {@code lang.*.yml}
 * files are gone; English text lives in {@code messages.properties}.
 *
 * @author Acrobot
 */
public class Messages {
    public static Message prefix;

    public static Message shopinfo;
    public static Message shopinfo_buy;
    public static Message shopinfo_sell;

    public static Message iteminfo;
    public static Message iteminfo_fullname;
    public static Message iteminfo_shopname;
    public static Message iteminfo_repaircost;
    public static Message iteminfo_book;
    public static Message iteminfo_book_generation;
    public static Message iteminfo_leather_color;
    public static Message iteminfo_bundle_items;
    public static Message iteminfo_axolotl_variant;
    public static Message iteminfo_armor_trim;
    public static Message iteminfo_recipes;
    public static Message iteminfo_map_view;
    public static Message iteminfo_map_location;
    public static Message iteminfo_tropical_fish;
    public static Message iteminfo_crossbow_projectiles;
    public static Message iteminfo_crossbow_projectile;
    public static Message iteminfo_lore;

    public static Message METRICS;

    public static Message ACCESS_DENIED;
    public static Message TRADE_DENIED;
    public static Message TRADE_DENIED_ACCESS_PERMS;
    public static Message TRADE_DENIED_CREATIVE_MODE;

    public static Message NOT_ENOUGH_MONEY;
    public static Message NOT_ENOUGH_MONEY_SHOP;

    public static Message CLIENT_DEPOSIT_FAILED;
    public static Message SHOP_DEPOSIT_FAILED;
    public static Message NO_ECONOMY_ACCOUNT;

    public static Message NO_BUYING_HERE;
    public static Message NO_SELLING_HERE;

    public static Message NOT_ENOUGH_SPACE_IN_INVENTORY;
    public static Message NOT_ENOUGH_SPACE_IN_CHEST;
    public static Message NOT_ENOUGH_ITEMS_TO_SELL;
    public static Message NOT_ENOUGH_SPACE_IN_YOUR_SHOP;

    public static Message NOT_ENOUGH_STOCK;
    public static Message NOT_ENOUGH_STOCK_IN_YOUR_SHOP;

    public static Message YOU_BOUGHT_FROM_SHOP;
    public static Message SOMEBODY_BOUGHT_FROM_YOUR_SHOP;

    public static Message YOU_SOLD_TO_SHOP;
    public static Message SOMEBODY_SOLD_TO_YOUR_SHOP;

    public static Message YOU_CANNOT_CREATE_SHOP;
    public static Message NO_CHEST_DETECTED;
    public static Message INVALID_SHOP_DETECTED;
    public static Message INVALID_SHOP_PRICE;
    public static Message INVALID_SHOP_QUANTITY;
    public static Message CANNOT_ACCESS_THE_CHEST;
    public static Message CANNOT_CHANGE_SIGN_BACKSIDE;

    public static Message SELL_PRICE_HIGHER_THAN_BUY_PRICE;
    public static Message SELL_PRICE_ABOVE_MAX;
    public static Message SELL_PRICE_BELOW_MIN;
    public static Message BUY_PRICE_ABOVE_MAX;
    public static Message BUY_PRICE_BELOW_MIN;

    public static Message CLICK_TO_AUTOFILL_ITEM;
    public static Message NO_ITEM_IN_HAND;

    public static Message PROTECTED_SHOP;
    public static Message PROTECTED_SHOP_SIGN;
    public static Message SHOP_CREATED;
    public static Message SHOP_FEE_PAID;
    public static Message SHOP_REFUNDED;
    public static Message ITEM_GIVEN;

    public static Message RESTRICTED_SIGN_CREATED;

    public static Message PLAYER_NOT_FOUND;
    public static Message NO_PERMISSION;
    public static Message INCORRECT_ITEM_ID;
    public static Message INVALID_CLIENT_NAME;
    public static Message NOT_ENOUGH_PROTECTIONS;
    public static Message NO_SHOP_FOUND;

    public static Message CANNOT_CREATE_SHOP_HERE;

    public static Message BUSINESS_ACCOUNT_NOT_FOUND;
    public static Message BUSINESS_NO_CHESTSHOP_PERMISSION;
    public static Message TREASURY_REQUIRED;

    public static Message TOGGLE_MESSAGES_OFF;
    public static Message TOGGLE_MESSAGES_ON;

    public static Message TOGGLE_ACCESS_ON;
    public static Message TOGGLE_ACCESS_OFF;

    public static Message ERROR_OCCURRED;

    /** Binds each static field to its {@code chestshop.<field>} key in messages.properties. */
    public static void load() {
        for (Field field : Messages.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != Message.class) {
                continue;
            }
            try {
                field.set(null, new Message("chestshop." + field.getName()));
            } catch (IllegalAccessException e) {
                ChestShop.getBukkitLogger().log(Level.SEVERE, "Error while setting Message " + field.getName() + "!", e);
            }
        }
    }

    /**
     * A handle to one {@code messages.properties} key. Rendered through the
     * framework {@code Message} bean ({@link ChestShop#message()}); placeholder
     * values are passed straight through (a {@link net.kyori.adventure.text.ComponentLike}
     * value — e.g. a formatted item name — renders inline).
     */
    public static class Message {
        private final String key;

        public Message(String key) {
            this.key = key;
        }

        public void sendWithPrefix(CommandSender sender, Map<String, String> replacementMap, String... replacements) {
            sender.sendMessage(getComponent(sender, true, replacementMap, replacements));
        }

        public void sendWithPrefix(CommandSender sender, Map<String, String> replacements) {
            sendWithPrefix(sender, replacements, new String[0]);
        }

        public void sendWithPrefix(CommandSender sender, String... replacements) {
            sendWithPrefix(sender, Collections.emptyMap(), replacements);
        }

        public void send(CommandSender sender, String... replacements) {
            sender.sendMessage(getComponent(sender, false, Collections.emptyMap(), replacements));
        }

        public void send(CommandSender sender, Map<String, String> replacements) {
            sender.sendMessage(getComponent(sender, false, replacements, new String[0]));
        }

        public Component getComponent(CommandSender sender, boolean prefixSuffix, Map<String, String> replacementMap, String... replacements) {
            return ChestShop.message().component(key, values(prefixSuffix, replacementMap, replacements));
        }

        /**
         * Collects placeholder values, honouring {@code prefixSuffix}: every template
         * begins with {@code {prefix}} (resolved from {@code placeholder.prefix}); when
         * the prefix is not wanted it is overridden to empty. Caller values are passed
         * as-is so a {@code ComponentLike} (item/player name) renders inline.
         */
        public static Map<String, Object> values(boolean prefixSuffix, Map<String, ?> replacementMap, String... replacements) {
            Map<String, Object> values = new LinkedHashMap<>();
            if (!prefixSuffix) {
                values.put("prefix", "");
            }
            if (replacementMap != null) {
                values.putAll(replacementMap);
            }
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                values.put(replacements[i], replacements[i + 1]);
            }
            return values;
        }

        public String getKey() {
            return key;
        }
    }
}
