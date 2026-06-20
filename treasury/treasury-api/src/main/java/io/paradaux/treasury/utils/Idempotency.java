package io.paradaux.treasury.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for generating idempotency (dedup) keys for Treasury ledger transactions.
 *
 * <p>The Treasury ledger stores dedup keys as {@code BINARY(32)} — exactly 32 bytes.
 * Always use {@link #sha256(String)} to derive a key from a domain string rather than
 * passing raw strings or UUIDs directly.
 *
 * <p>Recommended key construction pattern:
 * <pre>{@code
 * byte[] key = Idempotency.sha256("my-plugin:tax-type:" + ownerUuid + ":" + periodStart.toEpochMilli());
 * }</pre>
 */
public final class Idempotency {

    private Idempotency() {}

    /**
     * Returns the SHA-256 hash of {@code key} as a 32-byte array.
     * Safe to pass directly as the {@code dedupKey} parameter in
     * {@link io.paradaux.treasury.model.tax.TaxCollection} or
     * {@link io.paradaux.treasury.model.economy.TransferRequest}.
     *
     * @throws RuntimeException if SHA-256 is unavailable (never happens on a standard JVM)
     */
    public static byte[] sha256(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
