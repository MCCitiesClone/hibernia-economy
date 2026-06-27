package io.paradaux.chestshop.listeners.pretransaction;

import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.commands.Toggle;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.economy.Economy;
import io.paradaux.chestshop.events.PreTransactionEvent;
import io.paradaux.chestshop.utils.ItemUtil;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.UUID;

/**
 * @author Acrobot
 */
public class ErrorMessageSender {

    private static Table<UUID, String, Long> notificationCooldowns = HashBasedTable.create();

    public static void onQuit(PlayerQuitEvent event) {
        if (Properties.NOTIFICATION_MESSAGE_COOLDOWN > 0) {
            notificationCooldowns.rowMap().remove(event.getPlayer().getUniqueId());
        }
    }

    public static void onMessage(PreTransactionEvent event) {
        if (!event.isCancelled()) {
            return;
        }

        String message = null;

        switch (event.getTransactionOutcome()) {
            case SHOP_DOES_NOT_BUY_THIS_ITEM:
                message = "chestshop.NO_SELLING_HERE";
                break;
            case SHOP_DOES_NOT_SELL_THIS_ITEM:
                message = "chestshop.NO_BUYING_HERE";
                break;
            case CLIENT_DOES_NOT_HAVE_PERMISSION:
                message = "chestshop.NO_PERMISSION";
                break;
            case CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY:
                message = "chestshop.NOT_ENOUGH_MONEY";
                break;
            case SHOP_DOES_NOT_HAVE_ENOUGH_MONEY:
                message = "chestshop.NOT_ENOUGH_MONEY_SHOP";
                break;
            case NOT_ENOUGH_SPACE_IN_CHEST:
                if (Properties.SHOW_MESSAGE_FULL_SHOP && !Properties.CSTOGGLE_TOGGLES_FULL_SHOP || !Toggle.isIgnoring(event.getOwnerAccount().getUuid())) {
                    Location loc = event.getSign().getLocation();
                    sendMessageToOwner(event.getOwnerAccount(), "chestshop.NOT_ENOUGH_SPACE_IN_YOUR_SHOP", new String[]{
                            "price", Economy.formatBalance(event.getExactPrice()),
                            "seller", event.getClient().getName(),
                            "world", loc.getWorld().getName(),
                            "x", String.valueOf(loc.getBlockX()),
                            "y", String.valueOf(loc.getBlockY()),
                            "z", String.valueOf(loc.getBlockZ())
                    }, event.getStock());
                }
                message = "chestshop.NOT_ENOUGH_SPACE_IN_CHEST";
                break;
            case NOT_ENOUGH_SPACE_IN_INVENTORY:
                message = "chestshop.NOT_ENOUGH_SPACE_IN_INVENTORY";
                break;
            case NOT_ENOUGH_STOCK_IN_INVENTORY:
                message = "chestshop.NOT_ENOUGH_ITEMS_TO_SELL";
                break;
            case NOT_ENOUGH_STOCK_IN_CHEST:
                if (Properties.SHOW_MESSAGE_OUT_OF_STOCK && !Properties.CSTOGGLE_TOGGLES_OUT_OF_STOCK || !Toggle.isIgnoring(event.getOwnerAccount().getUuid())) {
                    Location loc = event.getSign().getLocation();
                    sendMessageToOwner(event.getOwnerAccount(), "chestshop.NOT_ENOUGH_STOCK_IN_YOUR_SHOP", new String[]{
                            "price", Economy.formatBalance(event.getExactPrice()),
                            "buyer", event.getClient().getName(),
                            "world", loc.getWorld().getName(),
                            "x", String.valueOf(loc.getBlockX()),
                            "y", String.valueOf(loc.getBlockY()),
                            "z", String.valueOf(loc.getBlockZ())
                    }, event.getStock());
                }
                message = "chestshop.NOT_ENOUGH_STOCK";
                break;
            case CLIENT_DEPOSIT_FAILED:
                message = "chestshop.CLIENT_DEPOSIT_FAILED";
                break;
            case SHOP_DEPOSIT_FAILED:
                sendMessageToOwner(event.getOwnerAccount(), "chestshop.CLIENT_DEPOSIT_FAILED", new String[0]);
                message = "chestshop.SHOP_DEPOSIT_FAILED";
                break;
            case SHOP_IS_RESTRICTED:
                message = "chestshop.ACCESS_DENIED";
                break;
            case INVALID_SHOP:
                message = "chestshop.INVALID_SHOP_DETECTED";
                break;
            case INVALID_CLIENT_NAME:
                message = "chestshop.INVALID_CLIENT_NAME";
                break;
            case CREATIVE_MODE_PROTECTION:
                message = "chestshop.TRADE_DENIED_CREATIVE_MODE";
                break;
            default:
                break;
        }

        if (message != null) {
            ChestShop.message().send(event.getClient(), message);
        }
    }

    private static void sendMessageToOwner(Account ownerAccount, String key, String[] replacements, ItemStack... stock) {
        Player player = Bukkit.getPlayer(ownerAccount.getUuid());
        if (player != null || Properties.BUNGEECORD_MESSAGES) {

            if (Properties.NOTIFICATION_MESSAGE_COOLDOWN > 0) {
                String cacheKey = key + "|" + String.join(",", replacements) + "|" + ItemUtil.getItemList(stock);
                Long last = notificationCooldowns.get(ownerAccount.getUuid(), cacheKey);
                if (last != null && last + Properties.NOTIFICATION_MESSAGE_COOLDOWN * 1000 > System.currentTimeMillis()) {
                    return;
                }
                notificationCooldowns.put(ownerAccount.getUuid(), cacheKey, System.currentTimeMillis());
            }

            if (player != null) {
                if (Properties.SHOWITEM_MESSAGE && MaterialUtil.Show.sendMessage(player, key, stock, Collections.emptyMap(), replacements)) {
                    return;
                }
                String items = ItemUtil.getItemList(stock);
                player.sendMessage(ChestShop.message().component(key,
                        ChestShop.values(true, ImmutableMap.of("material", items, "item", items), replacements)));
            } else {
                String items = ItemUtil.getItemList(stock);
                ChestShop.sendBungeeMessage(ownerAccount.getName(), key,
                        ImmutableMap.of("material", items, "item", items), replacements);
            }
        }
    }
}
