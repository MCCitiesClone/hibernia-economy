package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.utils.PriceUtil;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.signs.ChestShopSign;

import java.math.BigDecimal;

import static io.paradaux.chestshop.utils.PriceUtil.hasBuyPrice;
import static io.paradaux.chestshop.utils.PriceUtil.hasSellPrice;
import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.SELL_PRICE_HIGHER_THAN_BUY_PRICE;

/**
 * @author Acrobot
 */
public class PriceRatioChecker {

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
