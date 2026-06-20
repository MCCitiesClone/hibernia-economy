package io.paradaux.treasury.services;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sends in-game chat notifications to players when Treasury moves their money
 * behind the scenes — tax deductions and salary payouts. Offline players are
 * silently skipped (the underlying {@link io.paradaux.hibernia.framework.i18n.Message}
 * resolves the UUID to an online player and no-ops if absent).
 */
public interface EconomyNotifier {

    /**
     * Notify the owner of a personal account that tax was deducted from it.
     * No-op when the account is not a personal account, the owner is offline,
     * or the tax type is configured silent (see the implementation).
     *
     * @param payerAccountId the account the tax was charged to
     * @param taxType        the machine tax-type, e.g. {@code "personal-balance-tax"}
     * @param amount         the amount deducted
     */
    void notifyTaxCollected(int payerAccountId, String taxType, BigDecimal amount);

    /**
     * Notify a player that they received a government salary. No-op if offline.
     *
     * @param playerUuid the recipient
     * @param amount     the salary amount paid
     */
    void notifySalaryPaid(UUID playerUuid, BigDecimal amount);
}
