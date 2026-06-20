package io.paradaux.treasury.services;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.api.TaxApi;
import io.paradaux.treasury.mappers.AccountMapper;
import io.paradaux.treasury.mappers.PlayerLoginMapper;
import io.paradaux.treasury.model.config.BalanceTaxConfiguration;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountBalance;
import io.paradaux.treasury.model.tax.TaxCollection;
import io.paradaux.treasury.model.tax.TaxResult;
import io.paradaux.treasury.utils.Idempotency;
import io.paradaux.treasury.utils.Money;
import io.paradaux.treasury.utils.TreasuryConstants;
import org.jetbrains.annotations.NotNull;
import org.mybatis.guice.transactional.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes and collects the prorated personal balance tax on player login.
 *
 * <p>Tax formula:
 * <pre>
 *   proration   = seconds_since_last_login / (7 × 24 × 3600)
 *   weekly_rate = bracket_rate(balance)            // flat bracket, not marginal
 *   tax         = balance × weekly_rate × proration
 * </pre>
 *
 * <p>The new login time is recorded <em>before</em> tax collection so that a crash
 * mid-collection doesn't double-charge on the next login. The dedup key on the
 * {@link TaxCollection} prevents a second charge if the same login timestamp is
 * seen again (e.g. from a retry).
 */
@Slf4j
public class BalanceTaxService {

    /** Seconds in one week, used for proration. */
    private static final BigDecimal SECONDS_PER_WEEK = BigDecimal.valueOf(7L * 24 * 3600);

    private final BalanceTaxConfiguration config;
    private final TaxApi taxApi;
    private final AccountMapper accountMapper;
    private final PlayerLoginMapper loginMapper;

    /** Lazily cached destination account ID. */
    private volatile Integer destinationAccountId;

    /**
     * Per-player locks to serialise concurrent processLogin calls for the same UUID.
     * Prevents the TOCTOU race where two async tasks both read the old lastLogin value
     * and independently collect tax for the same period with different dedup keys.
     */
    private final ConcurrentHashMap<UUID, Object> loginLocks = new ConcurrentHashMap<>();

    @Inject
    public BalanceTaxService(BalanceTaxConfiguration config,
                             TaxApi taxApi,
                             AccountMapper accountMapper,
                             PlayerLoginMapper loginMapper) {
        this.config        = config;
        this.taxApi        = taxApi;
        this.accountMapper = accountMapper;
        this.loginMapper   = loginMapper;
    }

    /**
     * Called asynchronously on player join. Records the login, then collects
     * any personal balance tax owed since the previous login.
     *
     * @param playerUuid      the joining player's UUID
     * @param loginEpochSecs  the current login time as Unix epoch seconds
     */
    @Transactional
    public void processLogin(@NotNull UUID playerUuid, long loginEpochSecs) {
        // Serialise concurrent calls for the same player.
        // Without this lock, two async tasks (e.g. a real login racing with a manual
        // /tax trigger balance) can both read the same old lastLogin value and collect
        // tax for the same period with different dedup keys, doubling the charge.
        Object lock = loginLocks.computeIfAbsent(playerUuid, k -> new Object());
        synchronized (lock) {
            processLoginLocked(playerUuid, loginEpochSecs);
        }
    }

    private void processLoginLocked(@NotNull UUID playerUuid, long loginEpochSecs) {
        // 1. Read previous login (null = first ever join).
        Long lastLogin = loginMapper.findLastLogin(playerUuid);

        // 2. Record new login time before doing anything else.
        //    This prevents double-charging if collection fails and the player reconnects.
        loginMapper.upsertLogin(playerUuid, loginEpochSecs);

        if (lastLogin == null) {
            // First ever login — nothing to prorate against.
            return;
        }

        long secondsElapsed = loginEpochSecs - lastLogin;
        if (secondsElapsed <= 0) {
            // Clock skew or same-second reconnect — skip.
            return;
        }

        // 3. Resolve personal account.
        Integer accountId = accountMapper.findPersonalAccountId(playerUuid);
        if (accountId == null) {
            // Account hasn't been created yet (will be created by FirstPlayerJoinEvent).
            return;
        }

        // 4. Read current balance (pre-login snapshot is the taxable amount).
        AccountBalance ab = accountMapper.readBalance(accountId);
        if (ab == null) {
            return;
        }
        BigDecimal balance = ab.getBalance();
        if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 5. Determine bracket rate.
        BigDecimal weeklyRate = config.getWeeklyRate(balance);
        if (weeklyRate.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 6. Prorate over the elapsed period.
        BigDecimal proration = BigDecimal.valueOf(secondsElapsed)
                .divide(SECONDS_PER_WEEK, 10, RoundingMode.HALF_EVEN);

        BigDecimal taxAmount = balance
                .multiply(weeklyRate)
                .multiply(proration)
                .setScale(2, RoundingMode.HALF_EVEN);

        if (taxAmount.compareTo(Money.MINIMUM_AMOUNT) < 0) {
            return;
        }

        // 7. Build and collect. Dedup key = sha256("balance-tax:{uuid}:{loginEpoch}").
        byte[] dedupKey = Idempotency.sha256("balance-tax:" + playerUuid + ":" + loginEpochSecs);
        int destId = resolveDestinationAccountId();

        TaxCollection collection = TaxCollection.toAccount(
                accountId,
                destId,
                taxAmount,
                "personal-balance-tax",
                "Personal balance tax (" + formatPeriod(secondsElapsed) + " @ "
                        + weeklyRate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%/wk)",
                TreasuryConstants.VIRTUAL_TREASURY_INITIATOR,
                "treasury",
                dedupKey
        );

        TaxResult result = taxApi.collectTax(collection);

        if (result instanceof TaxResult.Collected c) {
            log.debug("Personal balance tax ${} collected from {} (txn {}, period {})",
                    c.amountCharged(), playerUuid, c.txnId(), formatPeriod(secondsElapsed));
        } else if (result instanceof TaxResult.Skipped s) {
            log.debug("Personal balance tax skipped for {} (period {}): {}",
                    playerUuid, formatPeriod(secondsElapsed), s.reason());
        } else if (result instanceof TaxResult.Failed f) {
            log.warn("Personal balance tax collection failed for {} (amount=${}, period={}): {}",
                    playerUuid, taxAmount, formatPeriod(secondsElapsed), f.errorMessage());
        }
    }

    // ---- Helpers ----

    private int resolveDestinationAccountId() {
        if (destinationAccountId == null) {
            synchronized (this) {
                if (destinationAccountId == null) {
                    String accountName = config.getGovernmentAccount();
                    Account account = accountMapper.findGovernmentAccountByName(accountName);
                    if (account != null) {
                        destinationAccountId = account.getAccountId();
                    } else {
                        log.warn("Balance tax government account '{}' not found — routing to default tax account",
                                accountName);
                        destinationAccountId = taxApi.getDefaultTaxAccountId();
                    }
                }
            }
        }
        return destinationAccountId;
    }

    /** Formats an elapsed-seconds value as "Xd Yh" for ledger descriptions. */
    private static String formatPeriod(long seconds) {
        long days  = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        long mins = (seconds % 3600) / 60;
        return hours + "h " + mins + "m";
    }
}
