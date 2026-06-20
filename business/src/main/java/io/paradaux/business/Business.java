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
import io.paradaux.business.listeners.FirmBalanceTaxListener;
import io.paradaux.business.listeners.FirmPlayerCreationEventListener;
import io.paradaux.business.model.config.DatabaseConfiguration;
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

        // 2) Create the injector, wiring:
        //    - BusinessModule (binds plugin + all config components + TreasuryApi)
        //    - DatabaseModule (needs the typed DatabaseConfiguration)
        //    - CommanderModule (commands)
        getLogger().info("Setting up dependency injection...");
        this.injector = Guice.createInjector(
                new BusinessModule(this, configLoader, treasuryApi),
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
        pm.registerEvents(injector.getInstance(FirmPlayerCreationEventListener.class), this);
        pm.registerEvents(injector.getInstance(FirmBalanceTaxListener.class), this);
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
