package io.paradaux.treasury.api;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TaxApiComputeTaxTest {

    @Test
    void computeTax_simpleProduct() {
        assertThat(TaxApi.computeTax(new BigDecimal("100.00"), new BigDecimal("0.05")))
                .isEqualByComparingTo("5.00");
    }

    @Test
    void computeTax_roundsHalfEvenAtTwoDecimalPlaces() {
        // 12.345 rounds to 12.34 (banker's rounding: round half to even)
        assertThat(TaxApi.computeTax(new BigDecimal("123.45"), new BigDecimal("0.10")))
                .isEqualByComparingTo("12.34");
        // 0.005 -> 0.00 (round to nearest even)
        assertThat(TaxApi.computeTax(new BigDecimal("0.50"), new BigDecimal("0.01")))
                .isEqualByComparingTo("0.00");
        // 0.015 -> 0.02
        assertThat(TaxApi.computeTax(new BigDecimal("1.50"), new BigDecimal("0.01")))
                .isEqualByComparingTo("0.02");
    }

    @Test
    void computeTax_zeroRateProducesZero() {
        assertThat(TaxApi.computeTax(new BigDecimal("9999.99"), BigDecimal.ZERO))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void computeTax_zeroAmountProducesZero() {
        assertThat(TaxApi.computeTax(BigDecimal.ZERO, new BigDecimal("0.10")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void computeTax_alwaysReturnsScaleTwo() {
        BigDecimal result = TaxApi.computeTax(new BigDecimal("1.00"), new BigDecimal("0.10"));
        assertThat(result.scale()).isEqualTo(2);
    }
}
