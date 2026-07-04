package io.paradaux.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverdraftPolicyTest {

    private static BigDecimal bd(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    // ---- isUnlimited: only the (allow_overdraft=true, credit_limit<0) faucet/sink sentinel ----

    @ParameterizedTest
    @CsvSource({
            "true,  -1,   true",   // the faucet/sink sentinel
            "true,  -0.01,true",   // any negative limit is the sentinel
            "true,  0,    false",  // finite zero floor, not unlimited
            "true,  100,  false",  // finite limit, not unlimited
            "false, -1,   false",  // overdraft off — the negative limit is inert
            "false, 0,    false",
    })
    void isUnlimited(boolean allowOverdraft, String creditLimit, boolean expected) {
        assertEqualsBool(expected, OverdraftPolicy.isUnlimited(allowOverdraft, bd(creditLimit)));
    }

    @Test
    void isUnlimitedNullLimitIsNeverUnlimited() {
        assertFalse(OverdraftPolicy.isUnlimited(true, null));
        assertFalse(OverdraftPolicy.isUnlimited(false, null));
    }

    // ---- isWithinFloor: the canonical three-way floor both engines share ----

    @ParameterizedTest
    @CsvSource({
            // balance, amount, allowOverdraft, creditLimit, expectedWithinFloor
            // allow_overdraft = false → floor 0, credit_limit ignored
            "100, 100, false, 0,   true",   // lands exactly on 0
            "100, 101, false, 0,   false",  // one over → insufficient
            "100, 101, false, 500, false",  // credit_limit ignored when overdraft is off
            // allow_overdraft = true, credit_limit < 0 → unlimited
            "0,   999999, true, -1, true",
            "-5000000, 1, true, -1, true",
            // allow_overdraft = true, credit_limit >= 0 → floor -credit_limit
            "0,   100, true, 100, true",    // 0-100 = -100, exactly the floor
            "0,   101, true, 100, false",   // -101 < -100 → insufficient
            "50,  100, true, 0,   false",   // floor 0: 50-100 = -50 < 0 → insufficient
            "100, 100, true, 0,   true",    // floor 0: lands on 0
    })
    void isWithinFloor(String balance, String amount, boolean allowOverdraft, String creditLimit, boolean expected) {
        assertEqualsBool(expected,
                OverdraftPolicy.isWithinFloor(bd(balance), bd(amount), allowOverdraft, bd(creditLimit)));
    }

    @Test
    void nullCreditLimitTreatedAsZeroFloor() {
        // allow_overdraft on but no limit set → floor 0 (never NPEs)
        assertTrue(OverdraftPolicy.isWithinFloor(bd("100"), bd("100"), true, null));
        assertFalse(OverdraftPolicy.isWithinFloor(bd("100"), bd("101"), true, null));
        // allow_overdraft off, null limit → floor 0
        assertFalse(OverdraftPolicy.isWithinFloor(bd("0"), bd("1"), false, null));
    }

    @Test
    void chestShopSystemAccountConfigIsFlooredAtZero() {
        // Regression: account_id=4 "ChestShop System" is (allow_overdraft=true, credit_limit=0).
        // Both engines must floor it at 0, not treat it as unlimited (the pre-fix REST divergence).
        assertTrue(OverdraftPolicy.isWithinFloor(bd("70.95"), bd("70.95"), true, bd("0")));
        assertFalse(OverdraftPolicy.isWithinFloor(bd("70.95"), bd("70.96"), true, bd("0")));
    }

    private static void assertEqualsBool(boolean expected, boolean actual) {
        if (expected) {
            assertTrue(actual);
        } else {
            assertFalse(actual);
        }
    }
}
