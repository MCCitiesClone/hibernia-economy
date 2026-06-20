package io.paradaux.business.model.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.business.Business;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Configuration for the corporate balance tax feature.
 *
 * <p>On each WEEKLY tax cycle, firms pay a flat-bracket balance tax on their
 * total balance across all accounts. The rate is determined by which bracket
 * the total balance falls into.
 *
 * <p>Tax formula:
 * <pre>
 *   weekly_rate = bracket_rate(total_balance)
 *   tax         = total_balance × weekly_rate
 * </pre>
 *
 * Tax is split proportionally across each account and charged individually.
 * Firms with the {@code balance-tax.exempt} property set to {@code true} are skipped.
 *
 * <p>Config location: {@code config.yml} under the {@code tax.balance:} key.
 */
@Singleton
public class BalanceTaxConfiguration {

    private static final NavigableMap<BigDecimal, BigDecimal> DEFAULT_BRACKETS;

    static {
        NavigableMap<BigDecimal, BigDecimal> m = new TreeMap<>();
        m.put(new BigDecimal("0.00"),       BigDecimal.ZERO);
        m.put(new BigDecimal("100000.00"),  new BigDecimal("0.01"));
        m.put(new BigDecimal("200000.00"),  new BigDecimal("0.012"));
        m.put(new BigDecimal("300000.00"),  new BigDecimal("0.014"));
        m.put(new BigDecimal("500000.00"),  new BigDecimal("0.018"));
        DEFAULT_BRACKETS = Collections.unmodifiableNavigableMap(m);
    }

    private final Business plugin;

    // Mutable so {@link #reload()} can refresh them at runtime — this is a Guice
    // singleton, so the tax listener/command holding a reference see new values
    // on the next cycle. {@code brackets} is replaced wholesale (always an
    // unmodifiable snapshot), never mutated in place.
    private boolean enabled;
    private String governmentAccount;
    private volatile NavigableMap<BigDecimal, BigDecimal> brackets;

    @Inject
    public BalanceTaxConfiguration(Business plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Re-reads the values from the (already-reloaded) {@code config.yml}. Callers
     * must run {@link Business#reloadConfig()} first so {@code getConfig()} is fresh.
     */
    public void reload() {
        load();
    }

    private void load() {
        Logger log = plugin.getLogger();
        FileConfiguration cfg = plugin.getConfig();

        this.enabled = cfg.getBoolean("tax.balance.enabled", false);

        String gov = cfg.getString("tax.balance.government-account", "DCGovernment");
        this.governmentAccount = (gov == null || gov.isBlank()) ? "DCGovernment" : gov;

        ConfigurationSection sec = cfg.getConfigurationSection("tax.balance.brackets");
        NavigableMap<BigDecimal, BigDecimal> map = new TreeMap<>();
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    BigDecimal min  = new BigDecimal(key);
                    BigDecimal rate = new BigDecimal(sec.getString(key, "0"));
                    if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                        log.warning("Balance tax bracket rate for floor '" + key + "' is out of range [0,1]: " + rate + " — skipping");
                        continue;
                    }
                    map.put(min, rate);
                } catch (NumberFormatException e) {
                    log.warning("Invalid balance tax bracket key '" + key + "' — skipping");
                }
            }
        }

        if (map.isEmpty()) {
            map.putAll(DEFAULT_BRACKETS);
        }
        this.brackets = Collections.unmodifiableNavigableMap(map);

        if (enabled) {
            log.info("Corporate balance tax enabled: " + brackets.size() + " bracket(s), government-account=" + governmentAccount);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getGovernmentAccount() {
        return governmentAccount;
    }

    /**
     * Returns the weekly tax rate applicable to the given total balance.
     * Uses a flat-bracket model: the entire balance is taxed at the rate
     * of the bracket it falls into (not a marginal/progressive system).
     *
     * @param balance the total firm balance to evaluate
     * @return the weekly rate (e.g. {@code 0.01} for 1 %), or {@code BigDecimal.ZERO} if none match
     */
    public BigDecimal getWeeklyRate(BigDecimal balance) {
        Map.Entry<BigDecimal, BigDecimal> entry = brackets.floorEntry(balance);
        return entry != null ? entry.getValue() : BigDecimal.ZERO;
    }
}
