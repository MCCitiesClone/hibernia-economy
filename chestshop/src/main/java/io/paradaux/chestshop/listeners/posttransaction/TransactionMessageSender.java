package io.paradaux.chestshop.listeners.posttransaction;

import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.commands.Toggle;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.economy.Economy;
import io.paradaux.chestshop.events.TransactionEvent;
import io.paradaux.chestshop.utils.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends the "you bought/sold" notifications after a trade settles. Invoked by
 * {@link io.paradaux.chestshop.services.TransactionService#process} as a MONITOR-order
 * step, only once the money leg has committed (a failed settlement cancels the
 * {@link TransactionEvent} and the pipeline stops before this) — it replaces the
 * message half of the old {@code CurrencyTransferEvent} fan-out.
 *
 * @author Acrobot
 */
public class TransactionMessageSender {

    public static void onTransaction(TransactionEvent event) {
        if (event.getTransactionType() == TransactionEvent.TransactionType.BUY) {
            sendBuyMessage(event);
        } else {
            sendSellMessage(event);
        }
    }

    protected static void sendBuyMessage(TransactionEvent event) {
        Player player = event.getClient();

        if (Properties.SHOW_TRANSACTION_INFORMATION_CLIENT) {
            sendMessage(player, event.getClient().getName(), "chestshop.YOU_BOUGHT_FROM_SHOP", event, "owner", event.getOwnerAccount().getName());
        }

        if (Properties.SHOW_TRANSACTION_INFORMATION_OWNER && !Toggle.isIgnoring(event.getOwnerAccount().getUuid())) {
            Player owner = Bukkit.getPlayer(event.getOwnerAccount().getUuid());
            sendMessage(owner, event.getOwnerAccount().getName(), "chestshop.SOMEBODY_BOUGHT_FROM_YOUR_SHOP", event, "buyer", player.getName());
        }
    }

    protected static void sendSellMessage(TransactionEvent event) {
        Player player = event.getClient();

        if (Properties.SHOW_TRANSACTION_INFORMATION_CLIENT) {
            sendMessage(player, event.getClient().getName(), "chestshop.YOU_SOLD_TO_SHOP", event, "buyer", event.getOwnerAccount().getName());
        }

        if (Properties.SHOW_TRANSACTION_INFORMATION_OWNER && !Toggle.isIgnoring(event.getOwnerAccount().getUuid())) {
            Player owner = Bukkit.getPlayer(event.getOwnerAccount().getUuid());
            sendMessage(owner, event.getOwnerAccount().getName(), "chestshop.SOMEBODY_SOLD_TO_YOUR_SHOP", event, "seller", player.getName());
        }
    }

    private static void sendMessage(Player player, String playerName, String key, TransactionEvent event, String... replacements) {
        Location loc = event.getSign().getLocation();
        Map<String, String> replacementMap = new LinkedHashMap<>();
        replacementMap.put("price", Economy.formatBalance(event.getExactPrice()));
        replacementMap.put("world", loc.getWorld().getName());
        replacementMap.put("x", String.valueOf(loc.getBlockX()));
        replacementMap.put("y", String.valueOf(loc.getBlockY()));
        replacementMap.put("z", String.valueOf(loc.getBlockZ()));
        replacementMap.put("material", "%item");

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            replacementMap.put(replacements[i], replacements[i + 1]);
        }

        if (Properties.SHOWITEM_MESSAGE && MaterialUtil.Show.sendMessage(player, playerName, key, event.getStock(), replacementMap)) {
            return;
        }

        if (player != null) {
            replacementMap.put("item", ItemUtil.getItemList(event.getStock()));
            player.sendMessage(ChestShop.message().component(key, ChestShop.values(true, replacementMap)));
        } else if (playerName != null) {
            replacementMap.put("item", ItemUtil.getItemList(event.getStock()));
            ChestShop.sendBungeeMessage(playerName, key, replacementMap);
        }
    }
}
