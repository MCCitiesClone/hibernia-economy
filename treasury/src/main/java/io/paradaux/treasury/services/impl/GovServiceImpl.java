package io.paradaux.treasury.services.impl;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.api.exceptions.FineAlreadyRevokedException;
import io.paradaux.treasury.api.exceptions.FineNotFoundException;
import io.paradaux.treasury.api.exceptions.GovAccountNotFoundException;
import io.paradaux.treasury.api.exceptions.InsufficientFineFundsException;
import io.paradaux.treasury.api.exceptions.PrimitiveAccountException;
import io.paradaux.treasury.mappers.GovernmentFineMapper;
import io.paradaux.treasury.model.config.GovernmentConfiguration;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountType;
import io.paradaux.treasury.model.economy.GovernmentFine;
import io.paradaux.treasury.model.economy.TransferRequest;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.GovService;
import io.paradaux.treasury.services.LedgerService;
import io.paradaux.treasury.utils.Idempotency;
import io.paradaux.treasury.utils.Money;
import io.paradaux.treasury.utils.TreasuryConstants;
import org.mybatis.guice.transactional.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
public class GovServiceImpl implements GovService {

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final GovernmentFineMapper fineMapper;
    private final GovernmentConfiguration govConfig;

    @Inject
    public GovServiceImpl(AccountService accountService,
                          LedgerService ledgerService,
                          GovernmentFineMapper fineMapper,
                          GovernmentConfiguration govConfig) {
        this.accountService = accountService;
        this.ledgerService  = ledgerService;
        this.fineMapper     = fineMapper;
        this.govConfig      = govConfig;
    }

    // ---- Account management ----

    @Override
    @Transactional
    public Account createDepartmentAccount(String name, UUID createdBy) {
        Account existing = accountService.getGovernmentAccountByName(name);
        if (existing != null) {
            throw new IllegalArgumentException("A GOVERNMENT account named '" + name + "' already exists");
        }
        Account created = accountService.createAccount(AccountType.GOVERNMENT, TreasuryConstants.VIRTUAL_TREASURY_OWNER, name);
        log.info("Department account '{}' (id={}) created by {}", name, created.getAccountId(), createdBy);
        return created;
    }

    @Override
    @Transactional
    public void archiveDepartmentAccount(String name, UUID archivedBy) {
        if (isPrimitiveName(name)) {
            throw new PrimitiveAccountException(name);
        }
        Account account = accountService.getGovernmentAccountByName(name);
        if (account == null) {
            throw new GovAccountNotFoundException(name);
        }
        accountService.archiveAccount(account.getAccountId());
        log.info("Department account '{}' (id={}) archived by {}", name, account.getAccountId(), archivedBy);
    }

    @Override
    @Transactional
    public List<Account> listGovernmentAccounts() {
        return accountService.listGovernmentAccounts();
    }

    // ---- Fine management ----

    @Override
    @Transactional
    public GovernmentFine issueFine(UUID player, BigDecimal amount, String reason, UUID issuedBy) {
        Money.requirePositive(amount, "fine amount > 0");

        String finesAccountName = govConfig.getFinesAccount();
        Account finesAccount = accountService.getGovernmentAccountByName(finesAccountName);
        if (finesAccount == null) {
            throw new GovAccountNotFoundException(finesAccountName);
        }
        return issueFine(player, finesAccount.getAccountId(), amount, reason, issuedBy);
    }

    @Override
    @Transactional
    public GovernmentFine issueFine(UUID player, int govAccountId, BigDecimal amount, String reason, UUID issuedBy) {
        Money.requirePositive(amount, "fine amount > 0");

        Account playerAccount = accountService.getAccountByUUID(player);
        if (playerAccount == null) {
            throw new IllegalArgumentException("No personal account found for player " + player);
        }

        Account govAccount = accountService.getAccountById(govAccountId);
        if (govAccount == null || govAccount.getAccountType() != AccountType.GOVERNMENT) {
            throw new GovAccountNotFoundException("account id " + govAccountId);
        }

        BigDecimal normalized = Money.normalize(amount);
        byte[] dedupKey = Idempotency.sha256("fine:" + issuedBy + ":" + player + ":"
                + Instant.now().truncatedTo(ChronoUnit.SECONDS));

        long txnId;
        try {
            txnId = ledgerService.transfer(new TransferRequest(
                    playerAccount.getAccountId(),
                    govAccount.getAccountId(),
                    normalized,
                    "Fine: " + reason,
                    issuedBy,
                    null,
                    TreasuryConstants.TREASURY_PLUGIN_NAME,
                    dedupKey
            ));
        } catch (IllegalStateException e) {
            // LedgerService.transfer throws IllegalStateException for
            // insufficient funds (and a few other failure modes). The fine
            // path only ever debits the target's PERSONAL account, so the
            // realistic cause here is "player can't afford the fine".
            throw new InsufficientFineFundsException(player, e);
        }

        GovernmentFine fine = GovernmentFine.builder()
                .playerUuid(player)
                .govAccountId(govAccount.getAccountId())
                .amount(normalized)
                .reason(reason)
                .txnId(txnId)
                .issuedBy(issuedBy)
                .build();
        fineMapper.insertFine(fine);
        log.info("Fine issued: id={} player={} account={} amount={} txn={} issuedBy={}",
                fine.getFineId(), player, govAccount.getAccountId(), normalized, txnId, issuedBy);
        return fine;
    }

    @Override
    @Transactional
    public GovernmentFine revokeFine(long fineId, UUID revokedBy) {
        GovernmentFine fine = fineMapper.findFineById(fineId);
        if (fine == null) {
            throw new FineNotFoundException(fineId);
        }
        if (fine.isRevoked()) {
            throw new FineAlreadyRevokedException(fineId);
        }

        Account playerAccount = accountService.getAccountByUUID(fine.getPlayerUuid());
        if (playerAccount == null) {
            throw new IllegalStateException("Player account not found for fine " + fineId);
        }
        Account finesAccount = accountService.getAccountById(fine.getGovAccountId());
        if (finesAccount == null) {
            throw new GovAccountNotFoundException("fines account for fine " + fineId);
        }

        byte[] dedupKey = Idempotency.sha256("fine-revoke:" + fineId + ":" + revokedBy);
        long revokeTxnId = ledgerService.transfer(new TransferRequest(
                finesAccount.getAccountId(),
                playerAccount.getAccountId(),
                fine.getAmount(),
                "Fine revoked: " + fine.getReason(),
                revokedBy,
                null,
                TreasuryConstants.TREASURY_PLUGIN_NAME,
                dedupKey
        ));

        fineMapper.revokeFine(fineId, revokedBy, revokeTxnId);
        fine.setRevoked(true);
        fine.setRevokedBy(revokedBy);
        fine.setRevokeTxnId(revokeTxnId);
        log.info("Fine revoked: id={} player={} amount={} reverseTxn={} revokedBy={}",
                fineId, fine.getPlayerUuid(), fine.getAmount(), revokeTxnId, revokedBy);
        return fine;
    }

    @Override
    @Transactional
    public GovernmentFine getFine(long fineId) {
        return fineMapper.findFineById(fineId);
    }

    @Override
    @Transactional
    public List<GovernmentFine> getPlayerFines(UUID player) {
        return fineMapper.findFinesByPlayer(player);
    }

    @Override
    @Transactional
    public List<GovernmentFine> getActivePlayerFines(UUID player) {
        return fineMapper.findActiveFinesByPlayer(player);
    }

    // ---- Private helpers ----

    private boolean isPrimitiveName(String name) {
        return name.equalsIgnoreCase(govConfig.getStartingBalancesAccount())
                || name.equalsIgnoreCase(govConfig.getTaxIncomeAccount())
                || name.equalsIgnoreCase(govConfig.getFinesAccount());
    }
}
