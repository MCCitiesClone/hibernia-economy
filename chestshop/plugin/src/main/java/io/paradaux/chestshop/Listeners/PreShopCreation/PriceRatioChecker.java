package io.paradaux.chestshop.Listeners.PreShopCreation;

import io.paradaux.chestshop.breeze.Utils.PriceUtil;
import io.paradaux.chestshop.Events.PreShopCreationEvent;
import io.paradaux.chestshop.Signs.ChestShopSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.math.BigDecimal;

import static io.paradaux.chestshop.breeze.Utils.PriceUtil.hasBuyPrice;
import static io.paradaux.chestshop.breeze.Utils.PriceUtil.hasSellPrice;
import static io.paradaux.chestshop.Events.PreShopCreationEvent.CreationOutcome.SELL_PRICE_HIGHER_THAN_BUY_PRICE;
import static org.bukkit.event.EventPriority.HIGH;

/**
 * @author Acrobot
 */
public class PriceRatioChecker implements Listener {

    @EventHandler(priority = HIGH)
    public static void onPreShopCreation(PreShopCreationEvent event) {
        String priceLine = ChestShopSign.getPrice(event.getSignLines());

        if (hasBuyPrice(priceLine) && hasSellPrice(priceLine)) {
            BigDecimal buyPrice = PriceUtil.getExactBuyPrice(priceLine);
            BigDecimal sellPrice = PriceUtil.getExactSellPrice(priceLine);
            if (sellPrice.compareTo(buyPrice) > 0) {
                event.setOutcome(SELL_PRICE_HIGHER_THAN_BUY_PRICE);
            }
        }
    }
}
