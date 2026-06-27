package io.paradaux.common;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the cross-trust-boundary invariant (ADT-22/23): mint and verify derive
 * the signing key through this one method, so it must be deterministic and stable.
 */
class JwtKeysTest {

    @Test
    void deriveHmacKey_isDeterministic_andProducesA256BitHmacKey() {
        SecretKey a = JwtKeys.deriveHmacKey("a-long-enough-shared-secret-value-123456");
        SecretKey b = JwtKeys.deriveHmacKey("a-long-enough-shared-secret-value-123456");

        assertEquals("HmacSHA256", a.getAlgorithm());
        assertEquals(32, a.getEncoded().length, "SHA-256 → 256-bit key");
        assertArrayEquals(a.getEncoded(), b.getEncoded(), "same secret must derive the same key");
    }

    @Test
    void differentSecrets_deriveDifferentKeys() {
        assertFalse(Arrays.equals(
                JwtKeys.deriveHmacKey("secret-one").getEncoded(),
                JwtKeys.deriveHmacKey("secret-two").getEncoded()));
    }
}
