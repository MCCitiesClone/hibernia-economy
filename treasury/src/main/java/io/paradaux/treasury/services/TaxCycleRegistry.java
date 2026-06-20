package io.paradaux.treasury.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.mappers.AccountMapper;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.tax.TaxCycleReport;
import io.paradaux.treasury.model.tax.TaxCycleType;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Owns mutable state related to tax cycles:
 * <ul>
 *   <li>The next-fire timestamp per cycle type, written by {@code TaxCycleTask}.</li>
 *   <li>The set of plugins that have called {@code TaxApi#registerCycleParticipant}.</li>
 *   <li>The active accumulator session, used to build a {@link TaxCycleReport} after each cycle.</li>
 * </ul>
 *
 * <p>Split out of {@code TaxApiImpl} so that the tax-collection logic stays
 * focused on collection. {@code TaxCycleTask} and {@code TaxCommand} inject
 * this registry directly instead of casting the {@link io.paradaux.treasury.api.TaxApi}
 * to its implementation type.
 */
@Slf4j
@Singleton
public class TaxCycleRegistry {

    private final AccountMapper accountMapper;

    private final Map<TaxCycleType, Instant> nextFireTimes = new EnumMap<>(TaxCycleType.class);
    private final Map<TaxCycleType, Set<String>> cycleParticipants = new EnumMap<>(TaxCycleType.class);

    private volatile CycleSession activeSession;

    @Inject
    public TaxCycleRegistry(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    // ---- Schedule introspection ----

    public Optional<Instant> getNextFireTime(TaxCycleType cycleType) {
        return Optional.ofNullable(nextFireTimes.get(cycleType));
    }

    public void notifyNextFire(TaxCycleType cycleType, Instant when) {
        nextFireTimes.put(cycleType, when);
    }

    // ---- Participant registration ----

    public void registerCycleParticipant(String pluginName, TaxCycleType... cycleTypes) {
        for (TaxCycleType type : cycleTypes) {
            cycleParticipants
                    .computeIfAbsent(type, k -> Collections.synchronizedSet(new LinkedHashSet<>()))
                    .add(pluginName);
        }
        log.debug("Registered cycle participant '{}' for {}", pluginName, (Object) cycleTypes);
    }

    public Set<String> getCycleParticipants(TaxCycleType cycleType) {
        Set<String> participants = cycleParticipants.get(cycleType);
        return participants != null ? Collections.unmodifiableSet(participants) : Set.of();
    }

    // ---- Cycle session ----

    public void startSession(TaxCycleType cycleType, Instant periodStart,
                             boolean manual, @Nullable String triggeredBy) {
        activeSession = new CycleSession(cycleType, periodStart, manual, triggeredBy);
    }

    public void recordCollected(int destinationAccountId, String taxType, BigDecimal amount) {
        CycleSession session = activeSession;
        if (session != null) session.recordCollected(destinationAccountId, taxType, amount);
    }

    public void recordSkipped() {
        CycleSession session = activeSession;
        if (session != null) session.recordSkipped();
    }

    public void recordFailed() {
        CycleSession session = activeSession;
        if (session != null) session.recordFailed();
    }

    public boolean hasActiveSession() {
        return activeSession != null;
    }

    public @Nullable TaxCycleReport endSession() {
        CycleSession session = activeSession;
        activeSession = null;
        if (session == null) return null;

        Map<Integer, String> idToName = new LinkedHashMap<>();
        for (CycleSession.Entry e : session.entries) {
            idToName.computeIfAbsent(e.destinationAccountId(), id -> {
                Account account = accountMapper.findById(id);
                return account != null ? account.getDisplayName() : "Account #" + id;
            });
        }

        Map<String, BigDecimal> byAccount = new LinkedHashMap<>();
        Map<String, BigDecimal> byTaxType = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CycleSession.Entry e : session.entries) {
            String accountName = idToName.get(e.destinationAccountId());
            byAccount.merge(accountName, e.amount(), BigDecimal::add);
            byTaxType.merge(e.taxType(), e.amount(), BigDecimal::add);
            total = total.add(e.amount());
        }

        return new TaxCycleReport(
                session.cycleType, session.periodStart, session.manual, session.triggeredBy,
                total, byAccount, byTaxType,
                session.collectedCount, session.skippedCount, session.failedCount
        );
    }

    // ---- Inner ----

    private static final class CycleSession {
        final TaxCycleType cycleType;
        final Instant periodStart;
        final boolean manual;
        final String triggeredBy;

        final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());
        volatile int collectedCount;
        volatile int skippedCount;
        volatile int failedCount;

        CycleSession(TaxCycleType cycleType, Instant periodStart, boolean manual, String triggeredBy) {
            this.cycleType   = cycleType;
            this.periodStart = periodStart;
            this.manual      = manual;
            this.triggeredBy = triggeredBy;
        }

        void recordCollected(int destinationAccountId, String taxType, BigDecimal amount) {
            entries.add(new Entry(destinationAccountId, taxType, amount));
            collectedCount++;
        }

        void recordSkipped() { skippedCount++; }
        void recordFailed()  { failedCount++; }

        record Entry(int destinationAccountId, String taxType, BigDecimal amount) {}
    }
}
