package io.paradaux.treasury.api;

import io.paradaux.treasury.model.tax.TaxCollection;
import io.paradaux.treasury.model.tax.TaxCycleType;
import io.paradaux.treasury.model.tax.TaxResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * API for collecting taxes via the Treasury double-entry ledger.
 *
 * <p>Obtain this via {@link TreasuryApi#getTaxApi()}.
 *
 * <p><b>Two usage patterns:</b>
 * <ol>
 *   <li><b>Event-driven (immediate):</b> Call {@link #collectTax} directly when your plugin
 *       processes a taxable event (e.g. a plot purchase). The call is synchronous and posts
 *       immediately to the ledger.</li>
 *   <li><b>Scheduled (recurring):</b> Listen for {@link io.paradaux.treasury.event.TaxCycleEvent}
 *       and call {@link #collectBatch} inside the handler. Treasury fires that event on its
 *       Bukkit scheduler (daily / weekly / monthly) so your plugin doesn't need its own timer.</li>
 * </ol>
 *
 * <p><b>Destination accounts:</b> Unless you have a specific reason to route taxes elsewhere,
 * use the {@code toDefaultAccount} factory methods on {@link TaxCollection} — or the
 * {@link #collectRateTax} convenience overloads — to automatically route proceeds to
 * Treasury's configured tax-collection account ({@link #getDefaultTaxAccountName}).
 */
public interface TaxApi {

    // ---- Core collection ----

    /**
     * Collect a single tax charge.
     *
     * <p>If {@link TaxCollection#destinationAccountId()} is {@code null}, proceeds are routed
     * to {@link #getDefaultTaxAccountId()}.
     *
     * <p>Returns {@link TaxResult.Skipped} (not an error) when:
     * <ul>
     *   <li>The amount is below the $0.01 minimum.</li>
     *   <li>A non-null dedup key was already seen for this charge.</li>
     * </ul>
     *
     * Returns {@link TaxResult.Failed} when the source account cannot be debited
     * (insufficient funds, account not found, etc.). No money moves on failure.
     */
    TaxResult collectTax(TaxCollection collection);

    /**
     * Collect multiple tax charges in a single call.
     *
     * <p>Each item is processed independently — a failure or skip on one entry does not
     * prevent the others from being collected. The returned list preserves input order.
     *
     * <p>Prefer this over looping {@link #collectTax} when collecting for many accounts
     * in a {@link io.paradaux.treasury.event.TaxCycleEvent} handler.
     */
    List<TaxResult> collectBatch(List<TaxCollection> collections);

    // ---- Convenience: rate-based collection ----

    /**
     * Compute {@code tax = transactionAmount × rate} (rounded HALF_EVEN, scale 2) and collect it
     * from {@code sourceAccountId} into the specified destination account.
     *
     * <p>Returns {@link TaxResult.Skipped} when the computed tax rounds to $0.00.
     *
     * @param sourceAccountId      Account that pays.
     * @param destinationAccountId Account that receives the tax.
     * @param transactionAmount    The base amount being taxed (e.g. a sale price).
     * @param rate                 Tax rate as a decimal fraction (e.g. {@code 0.05} for 5 %).
     * @param taxType              Machine-readable regime identifier, e.g. {@code "realty-sale-tax"}.
     * @param description          Human-readable ledger message.
     * @param initiator            UUID of the initiating entity.
     * @param pluginSystem         Plugin attribution for the ledger (may be {@code null}).
     * @param dedupKey             Idempotency key (may be {@code null}).
     */
    TaxResult collectRateTax(
            int sourceAccountId,
            int destinationAccountId,
            BigDecimal transactionAmount,
            BigDecimal rate,
            String taxType,
            String description,
            UUID initiator,
            @Nullable String pluginSystem,
            byte @Nullable [] dedupKey);

    /**
     * Same as {@link #collectRateTax(int, int, BigDecimal, BigDecimal, String, String, UUID, String, byte[])}
     * but routes proceeds to {@link #getDefaultTaxAccountId()}.
     */
    TaxResult collectRateTax(
            int sourceAccountId,
            BigDecimal transactionAmount,
            BigDecimal rate,
            String taxType,
            String description,
            UUID initiator,
            @Nullable String pluginSystem,
            byte @Nullable [] dedupKey);

    /**
     * Shortest overload — rate tax into the default account, no dedup key.
     * Suitable when the caller guarantees single-delivery (e.g. inside a unique transaction handler).
     */
    default TaxResult collectRateTax(
            int sourceAccountId,
            BigDecimal transactionAmount,
            BigDecimal rate,
            String taxType,
            String description,
            UUID initiator,
            @Nullable String pluginSystem) {
        return collectRateTax(sourceAccountId, transactionAmount, rate, taxType, description,
                initiator, pluginSystem, null);
    }

    // ---- Default tax account ----

    /**
     * The account ID of Treasury's configured default tax-collection account
     * (typically the {@code DCGovernment} GOVERNMENT account).
     *
     * <p>Use this when you want to construct a {@link TaxCollection} with an explicit
     * destination but still route to the same account as the default. Prefer the
     * {@code toDefaultAccount} factory methods when you don't need the ID directly.
     */
    int getDefaultTaxAccountId();

    /**
     * The display name of Treasury's configured default tax-collection account,
     * e.g. {@code "DCGovernment"}.
     */
    String getDefaultTaxAccountName();

    // ---- Cycle schedule introspection ----

    /**
     * Returns {@code true} if Treasury will fire
     * {@link io.paradaux.treasury.event.TaxCycleEvent} for the given cycle type.
     *
     * <p>Cycle types are individually enabled/disabled in Treasury's {@code config.yml}.
     * Plugins may call this on startup to warn operators when a required cycle is disabled.
     */
    boolean isCycleEnabled(TaxCycleType cycleType);

    /**
     * Returns the scheduled next fire time for a cycle, or {@link java.util.Optional#empty()}
     * if the cycle is disabled or has not been scheduled yet.
     *
     * <p>The value updates each time Treasury reschedules the task (i.e. after each successful
     * cycle run and on initial startup). Use this for status displays and monitoring.
     */
    Optional<Instant> getNextFireTime(TaxCycleType cycleType);

    // ---- Cycle participant registration ----

    /**
     * Declares that your plugin participates in the given tax cycle types.
     *
     * <p>Call this once from {@code onEnable()} after obtaining the TaxApi service reference.
     * Treasury uses this information for the {@code /tax status} display and monitoring webhooks.
     * It does not affect which events are fired or when — it is purely informational.
     *
     * @param pluginName  Your Bukkit plugin name (as returned by {@code getPlugin().getName()}).
     * @param cycleTypes  The cycle types your {@code TaxCycleEvent} listener handles.
     */
    default void registerCycleParticipant(String pluginName, TaxCycleType... cycleTypes) {}

    /**
     * Returns the plugin names that have registered for the given cycle type via
     * {@link #registerCycleParticipant}. Returns an empty set if none have registered.
     */
    default Set<String> getCycleParticipants(TaxCycleType cycleType) {
        return Set.of();
    }

    // ---- Source income tax ----

    /**
     * Applies source income tax to a player deposit.
     *
     * <p>The effective rate is determined by looking up {@code pluginName} in Treasury's
     * configured {@code tax.source-income-tax.plugin-rates} map, falling back to the configured
     * {@code default-rate}. Returns {@link TaxResult.Skipped} immediately when:
     * <ul>
     *   <li>The feature is disabled ({@code tax.source-income-tax.enabled: false}).</li>
     *   <li>The effective rate is zero.</li>
     *   <li>The computed tax rounds below the $0.01 minimum.</li>
     *   <li>The player has no Treasury account.</li>
     * </ul>
     *
     * <p>Treasury's Vault bridge calls this automatically after every successful
     * {@code depositPlayer()} call. Plugins using TreasuryApi directly may call it after
     * crediting a player to participate in the same income tax regime.
     *
     * @param playerUuid    The player who received the income.
     * @param depositAmount The gross deposit amount.
     * @param pluginName    The plugin responsible for the deposit (used for rate lookup).
     * @return {@link TaxResult.Collected} if tax was charged, {@link TaxResult.Skipped} if no tax
     *         applies, or {@link TaxResult.Failed} if the debit could not be completed.
     */
    TaxResult applySourceIncomeTax(@NotNull UUID playerUuid, @NotNull BigDecimal depositAmount,
                                   @NotNull String pluginName);

    // ---- Internal utility (available to both Treasury and consuming plugins) ----

    /**
     * Compute {@code amount × rate}, rounded HALF_EVEN to 2 decimal places.
     * The same rounding used internally by {@link #collectRateTax}.
     */
    static BigDecimal computeTax(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_EVEN);
    }
}
