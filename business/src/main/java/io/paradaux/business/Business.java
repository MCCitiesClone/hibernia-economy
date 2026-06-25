package io.paradaux.business;

import com.google.inject.Guice;
import com.google.inject.Injector;

import io.paradaux.hibernia.framework.commander.CommandManager;
import io.paradaux.hibernia.framework.configurator.ConfigurationLoader;
import io.paradaux.business.api.BusinessApi;
import io.paradaux.business.guice.BusinessModule;
import io.paradaux.business.guice.CommanderModule;
import io.paradaux.business.guice.DatabaseModule;
import io.paradaux.business.jobs.ExpireRequestsJob;
import io.paradaux.business.listeners.ChestShopSaleListener;
import io.paradaux.business.listeners.FirmBalanceTaxListener;
import io.paradaux.business.model.config.DatabaseConfiguration;
import io.paradaux.business.model.config.FirmConfiguration;
import io.paradaux.business.services.FirmSalesNotificationService;
import io.paradaux.treasury.api.SalesQueryApi;
import io.paradaux.treasury.api.TreasuryApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class Business extends JavaPlugin {

    private Injector injector;
    private ExpireRequestsJob expireRequestsJob;

    @Override
    public void onEnable() {
        getLogger().info("Loading configuration...");

        // 1) Load typed config components
        ConfigurationLoader configLoader = new ConfigurationLoader(this);
        configLoader.scanPackage("io.paradaux.business.model.config"); // <- fix package

        DatabaseConfiguration dbCfg = configLoader.getComponent(DatabaseConfiguration.class);
        if (dbCfg == null) {
            throw new IllegalStateException("DatabaseConfiguration not found. Check @ConfigurationComponent and package scan.");
        }

        // 1b) Obtain TreasuryApi from Bukkit services (Treasury plugin must be loaded first)
        RegisteredServiceProvider<TreasuryApi> rsp =
                Bukkit.getServicesManager().getRegistration(TreasuryApi.class);
        if (rsp == null) {
            getLogger().severe("Treasury plugin not found! Business plugin requires Treasury.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        TreasuryApi treasuryApi = rsp.getProvider();

        // 1c) SalesQueryApi — the read path into Treasury's chestshop_sale tracker,
        // registered by the same Treasury plugin (PAR-176). Required for /firm sales.
        RegisteredServiceProvider<SalesQueryApi> salesRsp =
                Bukkit.getServicesManager().getRegistration(SalesQueryApi.class);
        if (salesRsp == null) {
            getLogger().severe("Treasury SalesQueryApi not found! Update Treasury to a version that provides it.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        SalesQueryApi salesQueryApi = salesRsp.getProvider();

        // 2) Create the injector, wiring:
        //    - BusinessModule (binds plugin + all config components + Treasury APIs)
        //    - DatabaseModule (needs the typed DatabaseConfiguration)
        //    - CommanderModule (commands)
        getLogger().info("Setting up dependency injection...");
        this.injector = Guice.createInjector(
                new BusinessModule(this, configLoader, treasuryApi, salesQueryApi),
                new DatabaseModule(dbCfg),
                new CommanderModule(this)
        );

        // 3) Register commands (DI-managed)
        injector.getInstance(CommandManager.class).registerAll();

        // Register jobs
        expireRequestsJob = injector.getInstance(ExpireRequestsJob.class);
        expireRequestsJob.schedule();

        // 4) Register public API
        BusinessApi api = injector.getInstance(BusinessApi.class);
        getServer().getServicesManager().register(BusinessApi.class, api, this, ServicePriority.Normal);

        // 5) Register events
        var pm = this.getServer().getPluginManager();
        pm.registerEvents(injector.getInstance(FirmBalanceTaxListener.class), this);
        pm.registerEvents(injector.getInstance(ChestShopSaleListener.class), this);

        // 6) Drive the firm sale-notification digest: flush buffered sales on a
        // timer so bursts are condensed into one message per firm per window.
        FirmSalesNotificationService salesNotifications =
                injector.getInstance(FirmSalesNotificationService.class);
        long flushTicks = injector.getInstance(FirmConfiguration.class).getSalesNotifyFlushSeconds() * 20L;
        getServer().getScheduler().runTaskTimer(this, salesNotifications::flush, flushTicks, flushTicks);

        // 7) Register the employee-only firm chat channel with CarbonChat (PAR-20).
        // CarbonChat is a soft dependency: if it's absent, linking the Carbon-
        // referencing method throws NoClassDefFoundError at the call site (before
        // the method's own guard runs), so catch Throwable here to keep firm chat
        // optional without breaking enable.
        try {
            injector.getInstance(io.paradaux.business.chat.FirmChatService.class).initialise();
        } catch (Throwable t) {
            getLogger().info("CarbonChat unavailable — firm chat disabled (" + t.getClass().getSimpleName() + ").");
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);

        // Stop any scheduled tasks (ExpireRequestsJob etc.) before tearing down
        // the DI graph so they can't run with a half-closed DataSource.
        getServer().getScheduler().cancelTasks(this);

        try {
            javax.sql.DataSource ds = injector.getInstance(javax.sql.DataSource.class);
            if (ds instanceof com.zaxxer.hikari.HikariDataSource hikari) hikari.close();
        } catch (Exception ignored) { }
        injector = null;
    }
}
