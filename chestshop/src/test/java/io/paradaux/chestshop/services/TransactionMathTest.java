package io.paradaux.chestshop.services;

import io.paradaux.chestshop.configuration.ChestShopConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exact-arithmetic coverage for the partial-fulfilment money maths in
 * {@link TransactionService} (ADT-138): affordability (FLOOR to whole items),
 * price scaling (HALF_UP to the configured {@code PRICE_PRECISION}) and the
 * rounded-to-zero affordability guard. These are precisely the steps where a
 * scale/rounding/off-by-one slip would let a buyer underpay or an owner be
 * overpaid on a partial fill, and they previously had no direct coverage.
 */
class TransactionMathTest {

    private TransactionService serviceWithPrecision(int precision) {
        ChestShopConfiguration config = mock(ChestShopConfiguration.class);
        when(config.getPricePrecision()).thenReturn(precision);
        // Only config is consulted by scalePrice; every other collaborator is unused here.
        return new TransactionService(null, null, null, null, null, null, null, null,
                config, null, null, null, null);
    }

    // ── getAmountOfAffordableItems: floor(wallet / pricePerItem) ───────────────

    @Test
    void affordableItems_floorsToWholeItems() {
        assertThat(TransactionService.getAmountOfAffordableItems(new BigDecimal("10.00"), new BigDecimal("3.00"))).isEqualTo(3); // 3.33 -> 3
        assertThat(TransactionService.getAmountOfAffordableItems(new BigDecimal("9.00"), new BigDecimal("3.00"))).isEqualTo(3);  // exact
    }

    @Test
    void affordableItems_isZeroWhenCannotAffordEvenOne() {
        assertThat(TransactionService.getAmountOfAffordableItems(new BigDecimal("2.99"), new BigDecimal("3.00"))).isEqualTo(0);
        assertThat(TransactionService.getAmountOfAffordableItems(BigDecimal.ZERO, new BigDecimal("3.00"))).isEqualTo(0);
    }

    @Test
    void affordableItems_handlesNonTerminatingPerItemPrice() {
        // pricePerItem = 10 / 3 = 3.333...; with 10 in the wallet you can afford 3.
        BigDecimal pricePerItem = new BigDecimal("10.00").divide(new BigDecimal(3), MathContext.DECIMAL128);
        assertThat(TransactionService.getAmountOfAffordableItems(new BigDecimal("10.00"), pricePerItem)).isEqualTo(3);
    }

    // ── scalePrice: pricePerItem * count, rounded HALF_UP to PRICE_PRECISION ────

    @Test
    void scalePrice_roundsHalfUpToConfiguredScale() {
        TransactionService service = serviceWithPrecision(2);
        // 0.125 * 1 = 0.125 -> HALF_UP at 2dp -> 0.13
        BigDecimal scaled = service.scalePrice(new BigDecimal("0.125"), 1);
        assertThat(scaled).isEqualByComparingTo("0.13");
        assertThat(scaled.scale()).isEqualTo(2);
    }

    @Test
    void scalePrice_recombinesNonTerminatingPerItemPriceWithoutDrift() {
        TransactionService service = serviceWithPrecision(2);
        BigDecimal pricePerItem = new BigDecimal("10.00").divide(new BigDecimal(3), MathContext.DECIMAL128); // 3.333...
        // 3.333... * 3 = 9.999... -> HALF_UP at 2dp -> 10.00 (not 9.99)
        BigDecimal scaled = service.scalePrice(pricePerItem, 3);
        assertThat(scaled).isEqualByComparingTo("10.00");
        assertThat(scaled.scale()).isEqualTo(2);
    }

    @Test
    void scalePrice_honoursAHigherConfiguredPrecision() {
        TransactionService service = serviceWithPrecision(4);
        assertThat(service.scalePrice(new BigDecimal("0.12345"), 1)).isEqualByComparingTo("0.1235"); // HALF_UP at 4dp
    }

    // ── roundedToZero: a positive per-item price that scales to nothing ─────────

    @Test
    void roundedToZero_trueOnlyWhenPositivePriceScalesToZero() {
        assertThat(TransactionService.roundedToZero(new BigDecimal("0.001"), new BigDecimal("0.00"))).isTrue();
    }

    @Test
    void roundedToZero_falseForAFreeShop() {
        // pricePerItem 0 (free shop) is not "rounded to zero" — it is genuinely free.
        assertThat(TransactionService.roundedToZero(BigDecimal.ZERO, BigDecimal.ZERO)).isFalse();
    }

    @Test
    void roundedToZero_falseWhenScaledIsPositive() {
        assertThat(TransactionService.roundedToZero(new BigDecimal("0.01"), new BigDecimal("0.01"))).isFalse();
    }
}
