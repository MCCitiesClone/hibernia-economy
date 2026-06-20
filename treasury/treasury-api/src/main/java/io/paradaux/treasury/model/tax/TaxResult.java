package io.paradaux.treasury.model.tax;

import java.math.BigDecimal;

/**
 * The outcome of a {@link io.paradaux.treasury.api.TaxApi#collectTax} call.
 *
 * <p>Switch over the three permitted subtypes to handle each case:
 * <pre>{@code
 * switch (result) {
 *     case TaxResult.Collected c  -> logger.info("Collected {} → txn {}", c.amountCharged(), c.txnId());
 *     case TaxResult.Skipped  s  -> logger.debug("Skipped: {}", s.reason());
 *     case TaxResult.Failed   f  -> logger.warn("Tax failed: {}", f.errorMessage());
 * }
 * }</pre>
 */
public sealed interface TaxResult permits TaxResult.Collected, TaxResult.Skipped, TaxResult.Failed {

    /**
     * The tax was successfully collected and a ledger transaction was recorded.
     *
     * @param txnId                The Treasury ledger transaction ID for this collection.
     * @param amountCharged        The exact amount debited from the source account.
     * @param destinationAccountId The account that received the tax proceeds.
     */
    record Collected(long txnId, BigDecimal amountCharged, int destinationAccountId) implements TaxResult {}

    /**
     * The tax was intentionally not collected — no money moved and no ledger entry was written.
     * This is not an error; callers should log at DEBUG level.
     *
     * <p>Common reasons: amount below the $0.01 minimum, zero rate, duplicate dedup key.
     *
     * @param reason Human-readable explanation (suitable for debug logging).
     */
    record Skipped(String reason) implements TaxResult {}

    /**
     * The collection attempt failed due to an unexpected error (e.g. insufficient funds,
     * account not found, database error). No money moved.
     *
     * @param errorMessage Description of what went wrong.
     */
    record Failed(String errorMessage) implements TaxResult {}

    // ---- Convenience helpers ----

    /** {@code true} if the tax was actually collected. */
    default boolean isSuccess() {
        return this instanceof Collected;
    }

    /** {@code true} if the tax was intentionally skipped (not an error). */
    default boolean wasSkipped() {
        return this instanceof Skipped;
    }

    /** {@code true} if an unexpected error prevented collection. */
    default boolean hasFailed() {
        return this instanceof Failed;
    }
}
