package io.paradaux.chestshop.Tests;

import io.paradaux.chestshop.Configuration.Properties;
import io.paradaux.chestshop.Events.PreShopCreationEvent;
import io.paradaux.chestshop.Listeners.PreShopCreation.FreePriceChecker;
import io.paradaux.chestshop.Listeners.PreShopCreation.PriceChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Free ($0) shops are rejected at creation by default, and permitted when the
 * {@code ALLOW_FREE_SHOPS} config flag is set (PAR-88).
 */
public class FreePriceCheckerTest {

    @AfterEach
    void resetConfig() {
        Properties.ALLOW_FREE_SHOPS = false; // restore the default for other tests
    }

    private static PreShopCreationEvent run(String priceLine) {
        PreShopCreationEvent event = new PreShopCreationEvent(null, null, new String[]{null, null, priceLine, null});
        PriceChecker.onPreShopCreation(event);      // normalise the price line first (LOWEST)
        FreePriceChecker.onPreShopCreation(event);  // then the free-shop gate (NORMAL)
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
