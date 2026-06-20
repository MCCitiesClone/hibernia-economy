package io.paradaux.business.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.business.exceptions.NoFirmAccountException;
import io.paradaux.business.model.Firm;
import io.paradaux.business.model.FirmPlayer;
import io.paradaux.business.services.FirmPlayerService;
import io.paradaux.business.services.FirmService;
import io.paradaux.business.services.FirmTransactionService;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.Page;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountMember;
import io.paradaux.treasury.model.economy.TransactionEntry;
import io.paradaux.treasury.model.economy.TransferRequest;

import io.paradaux.business.model.FirmAccount;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Singleton
public class FirmTransactionServiceImpl implements FirmTransactionService {

    private final FirmService firms;
    private final TreasuryApi treasury;
    private final io.paradaux.business.mappers.FirmAccountsMapper firmAccounts;
    private final FirmPlayerService players;

    @Inject
    public FirmTransactionServiceImpl(FirmService firms, TreasuryApi treasury,
                                      io.paradaux.business.mappers.FirmAccountsMapper firmAccounts,
                                      FirmPlayerService players) {
        this.firms = firms;
        this.treasury = treasury;
        this.firmAccounts = firmAccounts;
        this.players = players;
    }

    @Override
    public BigDecimal getFirmBalance(Integer firmId) {
        int accountId = resolveAccountId(firmId);
        return treasury.getBalanceByAccountId(accountId);
    }

    @Override
    public String getFormattedBalance(Integer firmId) {
        BigDecimal balance = getFirmBalance(firmId);
        return treasury.formatAmount(balance);
    }

    @Override
    public Page<TransactionEntry> getTransactions(Integer firmId, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;
        int accountId = resolveAccountId(firmId);
        int offset = (page - 1) * pageSize;
        return treasury.getTransactionHistory(accountId, offset, pageSize);
    }

    @Override
    public long deposit(Integer firmId, UUID playerUuid, BigDecimal amount) {
        return deposit(firmId, playerUuid, amount, null);
    }

    @Override
    public long deposit(Integer firmId, UUID playerUuid, BigDecimal amount, String memo) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        int businessAccountId = resolveAccountId(firmId);
        Account personal = treasury.resolveOrCreatePersonal(playerUuid);

        if (!treasury.hasFunds(personal.getAccountId(), amount)) {
            throw new IllegalStateException("Insufficient personal funds.");
        }

        if (!treasury.canAccessAccount(playerUuid, businessAccountId)) {
            throw new SecurityException("You don't have access to this business account.");
        }

        TransferRequest req = new TransferRequest(
                personal.getAccountId(),
                businessAccountId,
                amount,
                depositReason(memo),
                playerUuid,
                null,
                "BusinessPlugin",
                null
        );

        return treasury.transfer(req);
    }

    /**
     * Builds the transfer reason for a deposit, optionally appending a player-supplied
     * memo. The reason column is VARCHAR(255), so the memo is sanitized (control chars
     * stripped, whitespace collapsed) and the whole string is capped to fit (PAR-10).
     */
    private static String depositReason(String memo) {
        String base = "Business deposit";
        if (memo == null) {
            return base;
        }
        String cleaned = memo.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return base;
        }
        String reason = base + ": " + cleaned;
        return reason.length() > 255 ? reason.substring(0, 255) : reason;
    }

    @Override
    public long withdraw(Integer firmId, UUID playerUuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        int businessAccountId = resolveAccountId(firmId);
        Account businessAccount = treasury.getAccountById(businessAccountId);
        Account personal = treasury.resolveOrCreatePersonal(playerUuid);

        if (!treasury.canAccessAccount(playerUuid, businessAccountId)) {
            throw new SecurityException("You don't have access to this business account.");
        }

        if (!treasury.hasFunds(businessAccountId, amount)) {
            throw new IllegalStateException("Insufficient business funds.");
        }

        // If the business account requires authorization, verify the player is an authorizer
        UUID authorizer = null;
        if (businessAccount.isRequiresAuthorization()) {
            List<AccountMember> authorizers = treasury.getAuthorizers(businessAccountId);
            boolean isAuth = authorizers.stream()
                    .anyMatch(a -> a.getMemberUuid().equals(playerUuid));
            if (!isAuth) {
                throw new SecurityException("You are not an authorizer for this business account.");
            }
            authorizer = playerUuid;
        }

        TransferRequest req = new TransferRequest(
                businessAccountId,
                personal.getAccountId(),
                amount,
                "Business withdrawal",
                playerUuid,
                authorizer,
                "BusinessPlugin",
                null
        );

        return treasury.transfer(req);
    }

    @Override
    public BigDecimal getAggregateBalance(Integer firmId) {
        List<FirmAccount> accounts = firmAccounts.listAccountsByFirm(firmId);
        // A firm with no live accounts (disbanded, or a corrupt/partial-heal state) has a
        // total balance of zero. Returning zero rather than throwing keeps read paths
        // (/firm info on a defunct firm, the disband prompt, the public BusinessApi) from
        // crashing; deposit/withdraw still surface the no-account condition via
        // resolveAccountId's NoFirmAccountException.
        BigDecimal total = BigDecimal.ZERO;
        for (FirmAccount account : accounts) {
            total = total.add(treasury.getBalanceByAccountId(account.getAccountId()));
        }
        return total;
    }

    @Override
    public String getFormattedAggregateBalance(Integer firmId) {
        return treasury.formatAmount(getAggregateBalance(firmId));
    }

    @Override
    public Page<TransactionEntry> getAggregateTransactions(Integer firmId, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;

        List<FirmAccount> accounts = firmAccounts.listAccountsByFirm(firmId);
        if (accounts.isEmpty()) {
            return new Page<>(List.of(), 0, 0, pageSize);
        }

        // Fetch enough transactions from each account to cover the requested page
        int fetchSize = page * pageSize;
        List<TransactionEntry> all = new ArrayList<>();
        for (FirmAccount account : accounts) {
            Page<TransactionEntry> accountTx = treasury.getTransactionHistory(account.getAccountId(), 0, fetchSize);
            all.addAll(accountTx.items());
        }

        // Sort by settlement time descending (most recent first)
        all.sort(Comparator.comparing(TransactionEntry::getSettlementTime).reversed());

        int totalCount = all.size();
        int offset = (page - 1) * pageSize;
        if (offset >= totalCount) {
            return new Page<>(List.of(), totalCount, offset, pageSize);
        }

        int end = Math.min(offset + pageSize, totalCount);
        return new Page<>(all.subList(offset, end), totalCount, offset, pageSize);
    }

    private int resolveAccountId(Integer firmId) {
        Firm firm = firms.getFirmByNameOrId(firmId.toString());
        if (firm == null) {
            throw new IllegalArgumentException("Firm not found: " + firmId);
        }

        // A default is only usable if the firm still owns that account. Archiving an
        // account soft-removes its firm_accounts link, so isFirmAccount also rejects a
        // default that has gone stale (points at an archived/removed account) — the
        // exact condition that made proprietors "unable to withdraw" (PAR-45/PAR-62).
        Integer current = firm.getDefaultAccountId();
        if (current != null && firmAccounts.isFirmAccount(firmId, current)) {
            return current;
        }

        // Unset or stale default: re-point to a surviving account so the displayed
        // default and the resolution path agree, then persist the heal. Any account
        // returned by getAnyAccountId is a live (non-removed) firm account.
        Integer survivor = firmAccounts.getAnyAccountId(firmId);
        if (survivor == null) {
            throw new NoFirmAccountException("Firm has no treasury account.");
        }

        firms.updateDefaultAccount(firmId, survivor);
        return survivor;
    }

    private void validateAccountBelongsToFirm(Integer firmId, Integer accountId) {
        if (!firmAccounts.isFirmAccount(firmId, accountId)) {
            throw new IllegalArgumentException("Account " + accountId + " does not belong to firm " + firmId);
        }
    }

    @Override
    public BigDecimal getAccountBalance(Integer firmId, Integer accountId) {
        validateAccountBelongsToFirm(firmId, accountId);
        return treasury.getBalanceByAccountId(accountId);
    }

    @Override
    public String getFormattedAccountBalance(Integer firmId, Integer accountId) {
        BigDecimal balance = getAccountBalance(firmId, accountId);
        return treasury.formatAmount(balance);
    }

    @Override
    public Page<TransactionEntry> getAccountTransactions(Integer firmId, Integer accountId, int page, int pageSize) {
        validateAccountBelongsToFirm(firmId, accountId);
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;
        int offset = (page - 1) * pageSize;
        return treasury.getTransactionHistory(accountId, offset, pageSize);
    }

    @Override
    public long depositToAccount(Integer firmId, Integer accountId, UUID playerUuid, BigDecimal amount) {
        validateAccountBelongsToFirm(firmId, accountId);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        Account businessAccount = treasury.getAccountById(accountId);
        if (businessAccount != null && businessAccount.isArchived()) {
            throw new IllegalStateException("Cannot deposit into an archived account.");
        }

        Account personal = treasury.resolveOrCreatePersonal(playerUuid);

        if (!treasury.hasFunds(personal.getAccountId(), amount)) {
            throw new IllegalStateException("Insufficient personal funds.");
        }

        if (!treasury.canAccessAccount(playerUuid, accountId)) {
            throw new SecurityException("You don't have access to this business account.");
        }

        TransferRequest req = new TransferRequest(
                personal.getAccountId(),
                accountId,
                amount,
                "Business deposit",
                playerUuid,
                null,
                "BusinessPlugin",
                null
        );

        return treasury.transfer(req);
    }

    @Override
    public long withdrawFromAccount(Integer firmId, Integer accountId, UUID playerUuid, BigDecimal amount) {
        validateAccountBelongsToFirm(firmId, accountId);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        Account businessAccount = treasury.getAccountById(accountId);
        Account personal = treasury.resolveOrCreatePersonal(playerUuid);

        if (!treasury.canAccessAccount(playerUuid, accountId)) {
            throw new SecurityException("You don't have access to this business account.");
        }

        if (!treasury.hasFunds(accountId, amount)) {
            throw new IllegalStateException("Insufficient business funds.");
        }

        UUID authorizer = null;
        if (businessAccount.isRequiresAuthorization()) {
            List<AccountMember> authorizers = treasury.getAuthorizers(accountId);
            boolean isAuth = authorizers.stream()
                    .anyMatch(a -> a.getMemberUuid().equals(playerUuid));
            if (!isAuth) {
                throw new SecurityException("You are not an authorizer for this business account.");
            }
            authorizer = playerUuid;
        }

        TransferRequest req = new TransferRequest(
                accountId,
                personal.getAccountId(),
                amount,
                "Business withdrawal",
                playerUuid,
                authorizer,
                "BusinessPlugin",
                null
        );

        return treasury.transfer(req);
    }

    // ---- PAYMENTS ---------------------------------------------------------------

    @Override
    public long payIntoFirm(Integer firmId, UUID payerUuid, BigDecimal amount) {
        int destAccountId = resolveAccountId(firmId);
        return payIn(destAccountId, payerUuid, amount,
                "Business payment: " + playerName(payerUuid) + " -> " + firmName(firmId));
    }

    @Override
    public long payIntoAccount(Integer firmId, Integer accountId, UUID payerUuid, BigDecimal amount) {
        validateAccountBelongsToFirm(firmId, accountId);
        return payIn(accountId, payerUuid, amount,
                "Business payment: " + playerName(payerUuid) + " -> " + firmName(firmId) + " #" + accountId);
    }

    @Override
    public long payPlayer(Integer firmId, UUID targetPlayerUuid, UUID actorUuid, BigDecimal amount) {
        int sourceAccountId = resolveAccountId(firmId);
        Account target = treasury.resolveOrCreatePersonal(targetPlayerUuid);
        return payOut(sourceAccountId, target.getAccountId(), actorUuid, amount,
                "Business payment: " + firmName(firmId) + " -> " + playerName(targetPlayerUuid));
    }

    @Override
    public long payPlayerFromAccount(Integer firmId, Integer accountId, UUID targetPlayerUuid, UUID actorUuid, BigDecimal amount) {
        validateAccountBelongsToFirm(firmId, accountId);
        Account target = treasury.resolveOrCreatePersonal(targetPlayerUuid);
        return payOut(accountId, target.getAccountId(), actorUuid, amount,
                "Business payment: " + firmName(firmId) + " #" + accountId + " -> " + playerName(targetPlayerUuid));
    }

    @Override
    public long payFirm(Integer sourceFirmId, Integer targetFirmId, UUID actorUuid, BigDecimal amount) {
        int sourceAccountId = resolveAccountId(sourceFirmId);
        int destAccountId = resolveAccountId(targetFirmId);
        if (sourceAccountId == destAccountId) {
            throw new IllegalArgumentException("Source and destination accounts are the same.");
        }
        return payOut(sourceAccountId, destAccountId, actorUuid, amount,
                "Business payment: " + firmName(sourceFirmId) + " -> " + firmName(targetFirmId));
    }

    @Override
    public long payFirmFromAccount(Integer sourceFirmId, Integer sourceAccountId, Integer targetFirmId, UUID actorUuid, BigDecimal amount) {
        validateAccountBelongsToFirm(sourceFirmId, sourceAccountId);
        int destAccountId = resolveAccountId(targetFirmId);
        if (sourceAccountId == destAccountId) {
            throw new IllegalArgumentException("Source and destination accounts are the same.");
        }
        return payOut(sourceAccountId, destAccountId, actorUuid, amount,
                "Business payment: " + firmName(sourceFirmId) + " #" + sourceAccountId + " -> " + firmName(targetFirmId));
    }

    /**
     * Inbound payment into a firm account. No access check on the payer — paying
     * money in is harmless — but the payer must have funds and the destination
     * must not be archived.
     */
    private long payIn(int destAccountId, UUID payerUuid, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        Account dest = treasury.getAccountById(destAccountId);
        if (dest != null && dest.isArchived()) {
            throw new IllegalStateException("Cannot pay into an archived account.");
        }

        Account personal = treasury.resolveOrCreatePersonal(payerUuid);
        if (!treasury.hasFunds(personal.getAccountId(), amount)) {
            throw new IllegalStateException("Insufficient personal funds.");
        }

        TransferRequest req = new TransferRequest(
                personal.getAccountId(),
                destAccountId,
                amount,
                description,
                payerUuid,
                null,
                "BusinessPlugin",
                null
        );

        return treasury.transfer(req);
    }

    /**
     * Outbound payment from a firm account. Mirrors {@link #withdrawFromAccount}'s
     * Treasury-level checks: the actor must be able to access the source account,
     * the source must have funds, and if the source requires authorization the
     * actor must be one of its authorizers. (Firm-finance permission is checked
     * by the command layer before reaching here.)
     */
    private long payOut(int sourceAccountId, int destAccountId, UUID actorUuid, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }

        Account source = treasury.getAccountById(sourceAccountId);

        if (!treasury.canAccessAccount(actorUuid, sourceAccountId)) {
            throw new SecurityException("You don't have access to this business account.");
        }

        if (!treasury.hasFunds(sourceAccountId, amount)) {
            throw new IllegalStateException("Insufficient business funds.");
        }

        UUID authorizer = null;
        if (source.isRequiresAuthorization()) {
            List<AccountMember> authorizers = treasury.getAuthorizers(sourceAccountId);
            boolean isAuth = authorizers.stream()
                    .anyMatch(a -> a.getMemberUuid().equals(actorUuid));
            if (!isAuth) {
                throw new SecurityException("You are not an authorizer for this business account.");
            }
            authorizer = actorUuid;
        }

        TransferRequest req = new TransferRequest(
                sourceAccountId,
                destAccountId,
                amount,
                description,
                actorUuid,
                authorizer,
                "BusinessPlugin",
                null
        );

        return treasury.transfer(req);
    }

    private String firmName(Integer firmId) {
        Firm firm = firms.getFirmByNameOrId(firmId.toString());
        return firm != null ? firm.getDisplayName() : ("firm#" + firmId);
    }

    private String playerName(UUID uuid) {
        return players.findByUuid(uuid).map(FirmPlayer::getCurrentName).orElse(uuid.toString());
    }
}
