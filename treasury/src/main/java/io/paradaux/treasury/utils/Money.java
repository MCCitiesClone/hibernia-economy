package io.paradaux.treasury.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money(){}

    public static final int FRACTIONAL_DIGITS = 2;
    public static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("0.01");

    public static BigDecimal normalize(BigDecimal v){ return v.setScale(FRACTIONAL_DIGITS, RoundingMode.HALF_EVEN); }

    public static void requirePositive(BigDecimal v, String msg){
        if (v == null || v.signum() <= 0) throw new IllegalArgumentException(msg);
        requireValidAmount(v);
    }

    public static void requireValidAmount(BigDecimal v) {
        requireValidScale(v);
        if (v.compareTo(MINIMUM_AMOUNT) < 0) {
            throw new IllegalArgumentException("Amount must be at least " + MINIMUM_AMOUNT);
        }
    }

    /**
     * Sub-cent precision check without a minimum-magnitude floor — for
     * admin {@code /eco set} where a zero balance is a legitimate target
     * but a sub-cent {@code 100.001} must be rejected outright rather
     * than silently rounded by {@link #normalize(BigDecimal)}.
     */
    public static void requireValidScale(BigDecimal v) {
        if (v.stripTrailingZeros().scale() > FRACTIONAL_DIGITS) {
            throw new IllegalArgumentException("Amount cannot have more than " + FRACTIONAL_DIGITS + " decimal places");
        }
    }
}
