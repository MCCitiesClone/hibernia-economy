package io.paradaux.chestshop.Listeners.PreShopCreation;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Configuration.Messages;
import io.paradaux.chestshop.Configuration.Properties;
import io.paradaux.chestshop.Economy.Economy;
import io.paradaux.chestshop.Events.Economy.CurrencyAddEvent;
import io.paradaux.chestshop.Events.Economy.CurrencySubtractEvent;
import io.paradaux.chestshop.Events.PreShopCreationEvent;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.Signs.ChestShopSign;
import io.paradaux.chestshop.UUIDs.NameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.math.BigDecimal;

import static io.paradaux.chestshop.Permission.NOFEE;

/**
 * @author Acrobot
 */
public class CreationFeeGetter implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public static void onShopCreation(PreShopCreationEvent event) {
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

        CurrencySubtractEvent subtractionEvent = new CurrencySubtractEvent(shopCreationPrice, player);
        ChestShop.callEvent(subtractionEvent);

        if (!subtractionEvent.wasHandled()) {
            event.setOutcome(PreShopCreationEvent.CreationOutcome.NOT_ENOUGH_MONEY);
            event.setSignLines(new String[4]);
            return;
        }

        if (NameManager.getServerEconomyAccount() != null) {
            CurrencyAddEvent currencyAddEvent = new CurrencyAddEvent(
                    shopCreationPrice,
                    NameManager.getServerEconomyAccount().getUuid(),
                    player.getWorld());
            ChestShop.callEvent(currencyAddEvent);
        }

        Messages.SHOP_FEE_PAID.sendWithPrefix(player, "amount", Economy.formatBalance(shopCreationPrice));
    }
}
