package io.paradaux.chestshop.services;

import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Free ($0) shops are rejected at creation by default, and permitted when
 * {@code ALLOW_FREE_SHOPS} is set (PAR-88). Exercises {@link ShopService#checkPrice}
 * (price-line normalise) then {@link ShopService#rejectFreeShop} (the free-shop gate),
 * the steps that were {@code PriceChecker}/{@code FreePriceChecker} before the creation
 * pipeline was folded into the service (PAR-282).
 */
class ShopFreePriceCheckTest {

    private final ShopService shops = new ShopService();

    @AfterEach
    void resetConfig() {
        Properties.ALLOW_FREE_SHOPS = false; // restore the default for other tests
    }

    private PreShopCreationEvent run(String priceLine) {
        PreShopCreationEvent event = new PreShopCreationEvent(null, null, new String[]{null, null, priceLine, null});
        shops.checkPrice(event);      // normalise the price line first (LOWEST)
        shops.rejectFreeShop(event);  // then the free-shop gate (NORMAL)
        return event;
    }

    @Test
    public void freeBuyShop_isRejectedByDefault() {
        assertTrue(run("B 0").isCancelled());
        assertTrue(run("B free").isCancelled());
    }

    @Test
    public void freeSellShop_isRejectedByDefault() {
        assertTrue(run("S 0").isCancelled());
    }

    @Test
    public void freeShop_isAllowedWhenConfigEnabled() {
        Properties.ALLOW_FREE_SHOPS = true;
        assertFalse(run("B 0").isCancelled());
        assertFalse(run("S 0").isCancelled());
        assertFalse(run("B 0:S 0").isCancelled());
    }

    @Test
    public void pricedShop_isUnaffected() {
        assertFalse(run("B 5").isCancelled());
        assertFalse(run("B 5:S 1").isCancelled());
    }
}
