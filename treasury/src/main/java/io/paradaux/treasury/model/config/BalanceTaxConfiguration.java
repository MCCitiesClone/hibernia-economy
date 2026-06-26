package io.paradaux.treasury.model.config;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.Treasury;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Configuration for the personal balance tax feature.
 *
 * <p>On each login, a player pays a prorated fraction of their weekly balance tax,
 * calculated over the time since their previous login. The rate applied to their
 * entire personal account balance is determined by which bracket the balance falls into.
 *
 * <p>Tax formula:
 * <pre>
 *   proration   = seconds_since_last_login / (7 × 24 × 3600)
 *   weekly_rate = bracket_rate(balance)
 *   tax         = balance × weekly_rate × proration
 * </pre>
 *
 * <p>Config location: {@code config.yml} under the {@code tax.balance:} key.
 */
@Slf4j
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

    // Mutable so {@link #reload()} can refresh them at runtime (Guice singleton,
    // read live per login). volatile so a balance-tax collection running on another
    // thread sees a fully-published value after /treasury reload re-populates these
    // in place, rather than a stale or torn read (ADT-30). {@code brackets} is
    // replaced wholesale.
    private volatile boolean enabled;
    private volatile String governmentAccount;
    /** Sorted ascending by minimum balance. Key = bracket floor, value = weekly rate. */
    private volatile NavigableMap<BigDecimal, BigDecimal> brackets;
    /** Set only via the {@code @Inject} ctor; null for test-factory instances. */
    private Treasury plugin;

    private BalanceTaxConfiguration(boolean enabled, String governmentAccount,
                                    NavigableMap<BigDecimal, BigDecimal> brackets) {
        this.enabled = enabled;
        this.governmentAccount = (governmentAccount == null || governmentAccount.isBlank())
                ? "DCGovernment" : governmentAccount;
        this.brackets = Collections.unmodifiableNavigableMap(new TreeMap<>(brackets));
    }

    /** Test-only factory: bypasses Bukkit config parsing. Production code uses the {@code @Inject} ctor. */
    public static BalanceTaxConfiguration forTesting(boolean enabled, String governmentAccount,
                                                     NavigableMap<BigDecimal, BigDecimal> brackets) {
        return new BalanceTaxConfiguration(enabled, governmentAccount, brackets);
    }

    @Inject
    public BalanceTaxConfiguration(Treasury plugin) {
        this.plugin = plugin;
        load();
    }

    /** Re-reads from the (already-reloaded) config. No-op for test instances. */
    public void reload() {
        if (plugin != null) load();
    }

    private void load() {
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
                        log.warn("Balance tax bracket rate for floor '{}' is out of range [0,1]: {} — skipping", key, rate);
                        continue;
                    }
                    map.put(min, rate);
                } catch (NumberFormatException e) {
                    log.warn("Invalid balance tax bracket key '{}' — skipping", key);
                }
            }
        }

        if (map.isEmpty()) {
            map.putAll(DEFAULT_BRACKETS);
        }
        this.brackets = Collections.unmodifiableNavigableMap(map);

        if (enabled) {
            log.info("Personal balance tax enabled: {} bracket(s), government-account={}",
                    brackets.size(), governmentAccount);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getGovernmentAccount() {
        return governmentAccount;
    }

    /**
     * Returns the weekly tax rate applicable to the given balance.
     * Uses a flat-bracket model: the entire balance is taxed at the rate
     * of the bracket it falls into (not a marginal/progressive system).
     *
     * @param balance the account balance to evaluate
     * @return the weekly rate (e.g. {@code 0.01} for 1 %), or {@code BigDecimal.ZERO} if none match
     */
    public BigDecimal getWeeklyRate(BigDecimal balance) {
        Map.Entry<BigDecimal, BigDecimal> entry = brackets.floorEntry(balance);
        return entry != null ? entry.getValue() : BigDecimal.ZERO;
    }
}
