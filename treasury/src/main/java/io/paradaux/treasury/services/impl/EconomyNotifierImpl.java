package io.paradaux.treasury.services.impl;

import com.google.inject.Inject;
import io.paradaux.hibernia.framework.i18n.Message;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountType;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.EconomyNotifier;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Sends player-facing chat messages for automated money movement.
 *
 * <p>Delivery is via {@link Message#send(UUID, String, Object...)}, which resolves
 * the UUID to an online player and silently no-ops if they are offline. Adventure
 * message sends are thread-safe on Paper, so this is safe to call from the async
 * tax-collection and salary-payout threads.
 */
@Slf4j
public class EconomyNotifierImpl implements EconomyNotifier {

    /**
     * Tax types that fire per-transaction (not per-cycle) and would spam the
     * recipient — suppressed from notifications. Periodic taxes (balance,
     * property) and salaries are always announced.
     */
    private static final Set<String> SILENT_TAX_TYPES = Set.of("source-income-tax");

    private final AccountService accountService;
    private final Message message;

    @Inject
    public EconomyNotifierImpl(AccountService accountService, Message message) {
        this.accountService = accountService;
        this.message = message;
    }

    @Override
    public void notifyTaxCollected(int payerAccountId, String taxType, BigDecimal amount) {
        if (taxType != null && SILENT_TAX_TYPES.contains(taxType)) {
            return;
        }
        // Only personal accounts map to a single notifiable player; firm/government
        // accounts paying tax have no individual to message.
        Account account = accountService.getAccountById(payerAccountId);
        if (account == null || account.getAccountType() != AccountType.PERSONAL || account.getOwnerUuid() == null) {
            return;
        }
        message.send(account.getOwnerUuid(), "treasury.tax.collected",
                "amount", accountService.formatAmount(amount),
                "tax", label(taxType));
    }

    @Override
    public void notifySalaryPaid(UUID playerUuid, BigDecimal amount) {
        message.send(playerUuid, "treasury.salary.received",
                "amount", accountService.formatAmount(amount));
    }

    /** Human-readable label for a machine tax-type, e.g. {@code property-tax} → {@code property tax}. */
    private static String label(String taxType) {
        if (taxType == null) {
            return "tax";
        }
        return "personal-balance-tax".equals(taxType) ? "balance tax" : taxType.replace('-', ' ');
    }
}
