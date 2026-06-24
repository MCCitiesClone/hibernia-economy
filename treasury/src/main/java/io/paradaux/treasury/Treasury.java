package io.paradaux.treasury;

import com.google.inject.*;
import com.google.inject.Module;
import com.zaxxer.hikari.HikariDataSource;
import io.paradaux.hibernia.framework.commander.CommandManager;
import io.paradaux.hibernia.framework.configurator.ConfigurationLoader;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.adapters.VaultEconomyRegistrar;
import io.paradaux.treasury.api.MarketApi;
import io.paradaux.treasury.api.SalesQueryApi;
import io.paradaux.treasury.api.TaxApi;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.api.impl.MarketApiImpl;
import io.paradaux.treasury.api.impl.SalesQueryApiImpl;
import io.paradaux.treasury.api.impl.TaxApiImpl;
import io.paradaux.treasury.api.impl.TreasuryApiImpl;
import io.paradaux.treasury.guice.*;
import io.paradaux.treasury.guice.providers.DataSourceProvider;
import io.paradaux.treasury.model.config.DatabaseConfiguration;
import io.paradaux.treasury.model.config.GovernmentConfiguration;
import io.paradaux.treasury.model.config.LoggingConfiguration;
import io.paradaux.treasury.model.config.SalaryConfiguration;
import io.paradaux.treasury.model.config.TaxCycleConfiguration;
import io.paradaux.treasury.services.LedgerService;
import io.paradaux.treasury.services.SalaryService;
import io.paradaux.treasury.services.TaxCycleRegistry;
import io.paradaux.treasury.services.TaxWebhookService;
import io.paradaux.treasury.tasks.SalaryTask;
import io.paradaux.treasury.tasks.TaxCycleTask;
import io.paradaux.treasury.utils.LoggingConfigurer;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
public final class Treasury extends JavaPlugin {

    @Getter
    private Injector injector;

    @Override
    public void onEnable() {
        // 1) Load configuration first so we can apply the configured log level
        //    before anything else gets a chance to spam the console.
        ConfigurationLoader configLoader = new ConfigurationLoader(this);
        configLoader.scanPackage("io.paradaux.treasury.model.config");

        LoggingConfiguration logCfg = configLoader.getComponent(LoggingConfiguration.class);
        if (logCfg != null) {
            LoggingConfigurer.apply(logCfg.getLevel());
        }

        log.info("Loading Treasury…");

        DatabaseConfiguration dbCfg = configLoader.getComponent(DatabaseConfiguration.class);
        if (dbCfg == null) {
            throw new IllegalStateException(
                    "DatabaseConfiguration not found. Check @ConfigurationComponent and package scan.");
        }

        // 2) Build the Guice injector. VaultModule is only installed when Vault
        //    is on the classpath, so VaultEconomyAdapter is bound conditionally.
        DataSource dataSource = new DataSourceProvider(
                dbCfg.getHost(),
                Integer.parseInt(dbCfg.getPort()),
                dbCfg.getDatabase(),
                dbCfg.getUsername(),
                dbCfg.getPassword(),
                Integer.parseInt(dbCfg.getPoolMaximumSize()),
                Integer.parseInt(dbCfg.getPoolMinimumIdle()),
                Long.parseLong(dbCfg.getPoolConnectionTimeoutMs()),
                Long.parseLong(dbCfg.getPoolMaxLifetimeMs()),
                Long.parseLong(dbCfg.getPoolKeepaliveMs())
        ).get();

        List<Module> modules = new ArrayList<>();
        modules.add(new TreasuryModule(this, configLoader));
        modules.add(new DatabaseModule(dataSource));
        modules.add(new CommanderModule(this));
        modules.add(new EventsModule());
        if (isVaultAvailable()) {
            modules.add(new VaultModule());
        }
        this.injector = Guice.createInjector(modules);

        // 3) Bootstrap primitive GOVERNMENT accounts (starting-balances, tax-income, fines).
        GovernmentConfiguration govCfg = injector.getInstance(GovernmentConfiguration.class);
        log.info("Government accounts: starting-balances='{}', tax-income='{}', fines='{}'",
                govCfg.getStartingBalancesAccount(),
                govCfg.getTaxIncomeAccount(),
                govCfg.getFinesAccount());
        injector.getInstance(LedgerService.class).bootstrapGovernmentAccounts();

        // 4) Register commands.
        injector.getInstance(CommandManager.class).registerAll();

        // 5) Register listeners (set bound by EventsModule).
        registerListeners();

        // 6) Register Vault economy if available.
        if (isVaultAvailable()) {
            VaultEconomyRegistrar.register(this, injector);
        } else {
            log.info("Vault not found. Vault economy provider will not be registered.");
        }

        // 7) Register Treasury and Tax APIs as Bukkit services.
        registerTreasuryApi();
        registerTaxApi();
        registerMarketApi();
        registerSalesQueryApi();
        scheduleSalaries();

        log.info("Treasury enabled.");
    }

    private void scheduleSalaries() {
        SalaryConfiguration salaryCfg = injector.getInstance(SalaryConfiguration.class);
        if (!salaryCfg.isEnabled()) {
            return;
        }
        SalaryService salaryService = injector.getInstance(SalaryService.class);
        new SalaryTask(this, salaryService).schedule(salaryCfg.getIntervalSeconds());
        log.info("Government salaries enabled (every {}s, {} group(s), from {})",
                salaryCfg.getIntervalSeconds(), salaryCfg.getAmounts().size(), salaryCfg.getGovernmentAccount());
    }

    @Override
    public void onDisable() {
        if (injector != null) {
            DataSource ds = injector.getInstance(DataSource.class);
            if (ds instanceof HikariDataSource hds) {
                hds.close();
            }
        }
        log.info("Treasury disabled.");
    }

    // ---- Helpers ----

    private void registerListeners() {
        Set<Listener> listeners = injector.getInstance(Key.get(new TypeLiteral<Set<Listener>>() {}));
        for (Listener listener : listeners) {
            Bukkit.getPluginManager().registerEvents(listener, this);
            log.debug("Registered listener: {}", listener.getClass().getSimpleName());
        }
    }

    private void registerTreasuryApi() {
        TreasuryApi api = injector.getInstance(TreasuryApiImpl.class);
        var existing = Bukkit.getServicesManager().getRegistration(TreasuryApi.class);
        if (existing != null) {
            log.info("Replacing existing TreasuryApi provider: {}", existing.getPlugin().getName());
            Bukkit.getServicesManager().unregister(existing.getProvider());
        }
        Bukkit.getServicesManager().register(TreasuryApi.class, api, this, ServicePriority.Highest);
    }

    private void registerTaxApi() {
        TaxApi taxApi = injector.getInstance(TaxApiImpl.class);
        Bukkit.getServicesManager().register(TaxApi.class, taxApi, this, ServicePriority.Highest);

        TaxCycleConfiguration cycleCfg = injector.getInstance(TaxCycleConfiguration.class);
        TaxCycleRegistry registry = injector.getInstance(TaxCycleRegistry.class);
        TaxWebhookService webhook = injector.getInstance(TaxWebhookService.class);

        if (cycleCfg.isDailyEnabled()) {
            TaxCycleTask.daily(this, taxApi, registry, webhook, cycleCfg.getDailyHour()).scheduleNext();
            log.info("Daily tax cycle enabled (hour={})", cycleCfg.getDailyHour());
        }
        if (cycleCfg.isWeeklyEnabled()) {
            TaxCycleTask.weekly(this, taxApi, registry, webhook,
                    cycleCfg.getWeeklyHour(), cycleCfg.getWeeklyDayOfWeek()).scheduleNext();
            log.info("Weekly tax cycle enabled (day={}, hour={})",
                    cycleCfg.getWeeklyDayOfWeek(), cycleCfg.getWeeklyHour());
        }
        if (cycleCfg.isMonthlyEnabled()) {
            TaxCycleTask.monthly(this, taxApi, registry, webhook,
                    cycleCfg.getMonthlyHour(), cycleCfg.getMonthlyDayOfMonth()).scheduleNext();
            log.info("Monthly tax cycle enabled (day-of-month={}, hour={})",
                    cycleCfg.getMonthlyDayOfMonth(), cycleCfg.getMonthlyHour());
        }
    }

    private void registerMarketApi() {
        MarketApi marketApi = injector.getInstance(MarketApiImpl.class);
        Bukkit.getServicesManager().register(MarketApi.class, marketApi, this, ServicePriority.Highest);
    }

    private void registerSalesQueryApi() {
        SalesQueryApi salesQueryApi = injector.getInstance(SalesQueryApiImpl.class);
        Bukkit.getServicesManager().register(SalesQueryApi.class, salesQueryApi, this, ServicePriority.Highest);
    }

    private boolean isVaultAvailable() {
        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
