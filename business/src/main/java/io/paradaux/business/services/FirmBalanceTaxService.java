package io.paradaux.business.services;

import io.paradaux.treasury.event.TaxCycleEvent;

/**
 * Owns the weekly corporate balance-tax computation: which firms are taxed, how
 * much each of their accounts contributes, and submitting the batch to Treasury.
 *
 * <p>The per-account allocation splits a firm's total tax proportionally across
 * its positive-balance accounts, settles each at currency precision (2dp), and
 * folds the rounding remainder into the largest-balance account so the
 * per-account sum matches the firm total exactly.
 */
public interface FirmBalanceTaxService {

    /**
     * Runs the weekly balance-tax cycle across all active firms and collects the
     * computed tax as a single batch.
     *
     * @param event the firing {@link TaxCycleEvent} (provides the tax API and period)
     * @return a tally of the batch outcome for the caller to log
     */
    BalanceTaxCycleResult runWeeklyCycle(TaxCycleEvent event);

    /** Outcome tally of a balance-tax batch. */
    record BalanceTaxCycleResult(long collected, long skipped, long failed) {}
}
