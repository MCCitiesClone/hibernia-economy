package io.paradaux.treasury.model.tax;

/**
 * Identifies the recurring schedule on which Treasury fires a {@link io.paradaux.treasury.event.TaxCycleEvent}.
 *
 * <p>Consuming plugins (e.g. Realty, Business) listen for this event and call
 * {@link io.paradaux.treasury.api.TaxApi#collectTax} inside the handler
 * for each account they need to tax during that cycle.
 *
 * <p>Treasury fires all three cycle types on its own Bukkit scheduler. Plugins
 * that only need a weekly cycle simply ignore {@code DAILY} and {@code MONTHLY} events.
 */
public enum TaxCycleType {

    /** Fires once per day (Treasury runs this at a configurable server-local hour). */
    DAILY,

    /** Fires once per week. */
    WEEKLY,

    /** Fires on the first day of each month. */
    MONTHLY
}
