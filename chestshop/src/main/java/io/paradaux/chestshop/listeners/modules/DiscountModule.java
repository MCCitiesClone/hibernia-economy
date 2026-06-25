package io.paradaux.chestshop.listeners.modules;

import io.paradaux.chestshop.utils.PriceUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.ChestShopReloadEvent;
import io.paradaux.chestshop.events.PreTransactionEvent;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.players.NameManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import static io.paradaux.chestshop.events.TransactionEvent.TransactionType.BUY;

/**
 * @author Acrobot
 */
public class DiscountModule implements Listener {
    private static final String DISCOUNT_MESSAGE = "Applied a discount of %1$f percent for a resulting price of %2$.2f";
    private YamlConfiguration config;
    private Set<String> groupList = new HashSet<String>();

    public DiscountModule() {
        load();
    }

    private void load() {
        config = YamlConfiguration.loadConfiguration(ChestShop.loadFile("discounts.yml"));

        config.options().header("This file is for discount management. You are able to do that:\n" +
                "group1: 75\n" +
                "That means that the person with ChestShop.discount.group1 permission will pay only 75% of the price. \n" +
                "For example, if the price is 100 dollars, the player pays only 75 dollars.\n" +
                "(Only works in buy-only Admin Shops!)");

        try {
            config.save(ChestShop.loadFile("discounts.yml"));
        } catch (IOException e) {
            ChestShop.getBukkitLogger().log(Level.SEVERE, "Error while loading discounts config", e);
        }

        groupList = config.getKeys(false);
    }

    @EventHandler
    public void onReload(ChestShopReloadEvent event) {
        load();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreTransaction(PreTransactionEvent event) {
        if (event.getTransactionType() != BUY || !NameManager.isAdminShop(event.getOwnerAccount().getUuid())) {
            return;
        }

        Player client = event.getClient();

        if (!PriceUtil.hasBuyPrice(ChestShopSign.getPrice(event.getSign()))) {
            return;
        }

        // groupList is an (unordered) HashSet, so picking the first matching
        // group gave a non-deterministic discount when a player held several.
        // Select the most favourable (lowest resulting percentage) instead, and
        // clamp each configured value to [0, 100] so a typo can't produce a
        // negative price or charge more than the sticker price.
        Double bestDiscount = null;
        for (String group : groupList) {
            if (Permission.has(client, Permission.DISCOUNT + group)) {
                double discount = Math.max(0, Math.min(100, config.getDouble(group)));
                if (bestDiscount == null || discount < bestDiscount) {
                    bestDiscount = discount;
                }
            }
        }

        if (bestDiscount == null) {
            return;
        }

        BigDecimal discountedPrice = event.getExactPrice().multiply(BigDecimal.valueOf(bestDiscount / 100));
        event.setExactPrice(discountedPrice);
        ChestShop.getBukkitLogger().info(String.format(DISCOUNT_MESSAGE, bestDiscount, discountedPrice));
    }
}
