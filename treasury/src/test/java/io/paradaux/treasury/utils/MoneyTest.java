package io.paradaux.treasury.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalize_roundsHalfEvenToTwoDecimals() {
        assertThat(Money.normalize(new BigDecimal("1.005"))).isEqualByComparingTo("1.00");
        assertThat(Money.normalize(new BigDecimal("1.015"))).isEqualByComparingTo("1.02");
        assertThat(Money.normalize(new BigDecimal("1.235"))).isEqualByComparingTo("1.24");
        assertThat(Money.normalize(new BigDecimal("1.245"))).isEqualByComparingTo("1.24");
    }

    @Test
    void normalize_preservesAlreadyNormalized() {
        assertThat(Money.normalize(new BigDecimal("100.00"))).isEqualByComparingTo("100.00");
        assertThat(Money.normalize(new BigDecimal("0.01"))).isEqualByComparingTo("0.01");
    }

    @Test
    void requirePositive_acceptsPositiveAmounts() {
        Money.requirePositive(new BigDecimal("0.01"), "should pass");
        Money.requirePositive(new BigDecimal("1.00"), "should pass");
        Money.requirePositive(new BigDecimal("9999.99"), "should pass");
    }

    @Test
    void requirePositive_rejectsZero() {
        assertThatThrownBy(() -> Money.requirePositive(BigDecimal.ZERO, "no zero"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no zero");
    }

    @Test
    void requirePositive_rejectsNegative() {
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("-0.01"), "no negative"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no negative");
    }

    @Test
    void requirePositive_rejectsNull() {
        assertThatThrownBy(() -> Money.requirePositive(null, "no null"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no null");
    }

    @Test
    void requirePositive_rejectsBelowMinimum() {
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("0.001"), "below"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount cannot have more than");
    }

    @Test
    void requireValidAmount_rejectsExtraDecimalPlaces() {
        assertThatThrownBy(() -> Money.requireValidAmount(new BigDecimal("1.234")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than 2 decimal places");
    }

    @Test
    void requireValidAmount_rejectsAmountBelowMinimum() {
        assertThatThrownBy(() -> Money.requireValidAmount(new BigDecimal("0.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least " + Money.MINIMUM_AMOUNT);
    }

    @Test
    void requireValidAmount_acceptsTrailingZerosBeyondTwoDecimals() {
        // stripTrailingZeros gives scale ≤ 2 here, so this should pass.
        Money.requireValidAmount(new BigDecimal("1.10"));
        Money.requireValidAmount(new BigDecimal("100.000"));
    }
}
