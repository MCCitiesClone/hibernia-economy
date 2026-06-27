package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.NOT_ENOUGH_MONEY;
import static io.paradaux.chestshop.Permission.NOFEE;

/**
 * @author Acrobot
 */
public class MoneyChecker {

    public static void onPreShopCreation(PreShopCreationEvent event) {
        BigDecimal shopCreationPrice = Properties.SHOP_CREATION_PRICE;

        if (shopCreationPrice.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        if (ChestShopSign.isAdminShop(event.getSignLines())) {
            return;
        }

        Player player = event.getPlayer();

        if (Permission.has(player, NOFEE)) {
            return;
        }

        if (!ChestShop.economy().hasFunds(player.getUniqueId(), shopCreationPrice)) {
            event.setOutcome(NOT_ENOUGH_MONEY);
        }
    }
}
