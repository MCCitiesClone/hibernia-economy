package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.breeze.utils.PriceUtil;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.math.BigDecimal;

import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.INVALID_PRICE;

/**
 * DemocracyCraft: free shops are not allowed — a buy or sell price of exactly 0
 * (the {@code b:0} / {@code s:0} case). Runs after {@link PriceChecker} (LOWEST)
 * has normalised the price line, reads the resulting buy/sell prices, and
 * rejects creation if either offered side is priced at 0. An <em>unoffered</em>
 * side ({@link PriceUtil#NO_PRICE} / -1) is fine — a buy-only or sell-only shop
 * with a non-zero price is unaffected.
 *
 * <p>Disabled when {@link Properties#ALLOW_FREE_SHOPS} is set, letting operators
 * opt into free shops (PAR-88).
 */
public class FreePriceChecker implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPreShopCreation(PreShopCreationEvent event) {
        if (Properties.ALLOW_FREE_SHOPS) {
            return;
        }
        String price = ChestShopSign.getPrice(event.getSignLines());
        if (isFree(PriceUtil.getExactBuyPrice(price)) || isFree(PriceUtil.getExactSellPrice(price))) {
            event.setOutcome(INVALID_PRICE);
        }
    }

    private static boolean isFree(BigDecimal price) {
        return price.compareTo(PriceUtil.FREE) == 0;
    }
}
