package io.paradaux.treasury.services;

import io.paradaux.treasury.model.Page;
import io.paradaux.treasury.model.economy.*;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerService {

    // ---- Startup ----

    /**
     * Ensures all primitive GOVERNMENT accounts exist (starting-balances, tax-income, fines).
     * Safe to call multiple times; no-ops if accounts already exist.
     * Must be called once from Treasury.onEnable() after the injector is created.
     */
    void bootstrapGovernmentAccounts();

    // ---- Player account lifecycle ----

    /**
     * Returns the PERSONAL account for the given player, creating and funding it
     * with the configured starting balance if it does not yet exist.
     */
    Account resolveOrCreatePersonal(UUID playerUuid);

    // ---- Transfers ----

    /** Direct value transfer (no plugin SYSTEM postings). */
    long transfer(TransferRequest req);

    /**
     * Admin override transfer: identical to {@link #transfer(TransferRequest)} but
     * bypasses the {@code requires_authorization} gate on either account. Intended
     * for console / {@code treasury.admin.transfer} operators who are the authority
     * by virtue of the permission, not account membership. Overdraft / insufficient
     * funds rules still apply.
     */
    long adminTransfer(TransferRequest req);

    /** Vault-style: withdraw from player's account into plugin SYSTEM (burn). */
    long vaultWithdraw(String pluginName, UUID playerUuid, BigDecimal amount, String memo,
                       UUID initiator, @Nullable UUID authorizer, @Nullable String idempotencyKey);

    /** Vault-style: deposit from plugin SYSTEM (mint) into player's account. */
    long vaultDeposit(String pluginName, UUID playerUuid, BigDecimal amount, String memo,
                      UUID initiator, @Nullable UUID authorizer, @Nullable String idempotencyKey);

    // ---- Admin economy operations ----

    /** Admin give: mint money from the "Eco" GOVERNMENT account to a player. */
    long adminGive(UUID playerUuid, BigDecimal amount, String memo, UUID adminUuid);

    /** Admin take: burn money from a player into the "Eco" GOVERNMENT account. */
    long adminTake(UUID playerUuid, BigDecimal amount, String memo, UUID adminUuid);

    /**
     * Admin set: adjusts a player's balance to exactly {@code targetAmount} by
     * issuing a give or take against the Eco account.
     * Returns the txn ID of the adjustment, or empty if the balance was already correct.
     */
    Optional<Long> adminSet(UUID playerUuid, BigDecimal targetAmount, String memo, UUID adminUuid);

    /** Admin reset: sets a player's balance to the configured starting balance. */
    void adminReset(UUID playerUuid, UUID adminUuid);

    // ---- Transaction history ----

    /** Paginated transaction history for an account (most recent first). */
    Page<TransactionEntry> getTransactionHistory(int accountId, int offset, int limit);

    /** Single transaction lookup by ID. */
    LedgerTxn getTransaction(long txnId);

    /** All postings belonging to a transaction. */
    List<LedgerPosting> getPostingsForTransaction(long txnId);
}
