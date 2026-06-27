package io.paradaux.common;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Single source of truth for deriving the HMAC-SHA256 signing key from the
 * configured JWT secret (ADT-22).
 *
 * <p>The mint side (treasury-api-plugin) and the verify side (treasury-rest-api)
 * MUST derive the key identically or every issued token fails verification. They
 * used to do it independently, tied only by a comment — if one changed the
 * digest, all tokens would silently break. Centralising it here removes that
 * footgun and gives both sides one place (and one test) to evolve the KDF.
 */
public final class JwtKeys {

    private JwtKeys() {}

    /**
     * Derives the HMAC-SHA256 key as {@code SHA-256(secret)}.
     *
     * <p>This is the historical derivation kept identical across the trust
     * boundary. Hardening it (random key material / HKDF) is a coordinated
     * change of this one method plus a secret rotation — see ADT-23.
     */
    public static SecretKey deriveHmacKey(String secret) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
