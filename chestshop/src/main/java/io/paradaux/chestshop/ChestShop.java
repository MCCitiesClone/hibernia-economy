package io.paradaux.chestshop;

import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.commands.Give;
import io.paradaux.chestshop.commands.ItemInfo;
import io.paradaux.chestshop.commands.ShopInfo;
import io.paradaux.chestshop.commands.Toggle;
import io.paradaux.chestshop.commands.Version;
import io.paradaux.chestshop.market.MarketHook;
import io.paradaux.chestshop.market.MarketListener;
import io.paradaux.chestshop.listeners.BlockPlace;
import io.paradaux.chestshop.listeners.ChestBreak;
import io.paradaux.chestshop.listeners.SignBreak;
import io.paradaux.chestshop.listeners.SignCreate;
import io.paradaux.chestshop.listeners.GarbageTextListener;
import io.paradaux.chestshop.listeners.ItemMoveListener;
import io.paradaux.chestshop.listeners.StockCounterModule;
import io.paradaux.chestshop.listeners.PlayerConnect;
import io.paradaux.chestshop.listeners.PlayerInteract;
import io.paradaux.chestshop.listeners.PlayerInventory;
import io.paradaux.chestshop.listeners.PlayerTeleport;
import io.paradaux.chestshop.utils.FileFormatter;
import io.paradaux.chestshop.integration.Dependencies;
import io.paradaux.chestshop.services.ItemCodeService;
import io.paradaux.chestshop.signs.RestrictedSign;


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * Main file of the plugin
 *
 * @author Acrobot
 */
public class ChestShop extends JavaPlugin {
    private static ChestShop plugin;
    private static Server server;
    private static PluginDescriptionFile description;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();


    // Instance references the plugin main class resolves at enable for its own
    // orchestration (DB migration, account-cache load). Not a service locator — every
    // other class injects its collaborators (PAR-282, PAR-300).
    private ItemCodeService itemCodes;
    private io.paradaux.chestshop.services.AccountService accounts;

    private static File dataFolder;

    private static Logger logger;
    private static Logger shopLogger;
    private FileHandler handler;

    private com.google.inject.Injector injector;
    private io.paradaux.hibernia.framework.configurator.ConfigurationLoader configurationLoader;
    // The framework Configurator repopulates this same component instance in place on
    // reload() (identity preserved), so injected references stay valid and this field can
    // be captured once for the plugin's own metrics/logging reads (PAR-282).
    private ChestShopConfiguration config;

    public ChestShop() {
        dataFolder = getDataFolder();
        logger = getLogger();
        shopLogger = Logger.getLogger("ChestShop Shops");
        shopLogger.setParent(logger);
        description = getDescription();
        server = getServer();
        plugin = this;
    }

    @Override
    public void onLoad() {
        Dependencies.initializePlugins();
    }

    @Override
    public void onEnable() {
        turnOffDatabaseLogging();

        // HiberniaFramework DI: loads ChestShopConfiguration from config.yml (and
        // additively reconciles new default keys on upgrade). The framework also
        // owns command registration now (PAR-264): the seven ChestShop commands
        // are CommandHandlers registered through Brigadier via CommandManager.
        // The framework Message bean is bound (no withoutMessages()) so both the
        // commander's error feedback and ChestShop's own command/listener output
        // render from messages.properties via the injected framework Message bean.
        io.paradaux.hibernia.framework.guice.HiberniaModule hibernia =
                io.paradaux.hibernia.framework.guice.HiberniaModule.forPlugin(this)
                        .scanConfiguration("io.paradaux.chestshop.model.config")
                        .handlers(io.paradaux.chestshop.commands.ChestShopCommand.class,
                                io.paradaux.chestshop.commands.BypassCommand.class,
                                ItemInfo.class, ShopInfo.class, Version.class,
                                io.paradaux.chestshop.commands.Metrics.class, Give.class,
                                Toggle.class,
                                io.paradaux.chestshop.find.FindCommand.class)
                        // Usher dialog handlers — the /find search flow (first Usher use).
                        .dialogs(io.paradaux.chestshop.find.FindDialog.class)
                        // Bukkit entrypoints — Guice-constructed so each injects the
                        // services it needs (listener → service → persistence), and
                        // registered in one call via ListenerManager#registerAll (PAR-282).
                        .listeners(
                                Dependencies.class,
                                SignBreak.class,
                                io.paradaux.chestshop.listeners.PhysicsBreak.class,
                                io.paradaux.chestshop.listeners.PaperBlockDestroyListener.class,
                                ChestBreak.class,
                                SignCreate.class,
                                BlockPlace.class,
                                io.paradaux.chestshop.listeners.SignBacksideProtector.class,
                                MarketListener.class,
                                PlayerConnect.class,
                                PlayerInteract.class,
                                PlayerInventory.class,
                                PlayerTeleport.class,
                                GarbageTextListener.class,
                                RestrictedSign.class,
                                StockCounterModule.class,
                                ItemMoveListener.class,
                                io.paradaux.chestshop.find.preview.PreviewListener.class)
                        .build();
        this.injector = com.google.inject.Guice.createInjector(hibernia,
                new io.paradaux.chestshop.guice.ChestShopModule(),
                new io.paradaux.chestshop.guice.DatabaseModule(loadFile("users.db"), loadFile("items.db")));
        this.configurationLoader = injector.getInstance(
                io.paradaux.hibernia.framework.configurator.ConfigurationLoader.class);
        itemCodes = injector.getInstance(io.paradaux.chestshop.services.ItemCodeService.class);
        accounts = injector.getInstance(io.paradaux.chestshop.services.AccountService.class);

        injector.getInstance(io.paradaux.hibernia.framework.commander.CommandManager.class).registerAll();

        loadConfig();

        itemCodes.migrateIfNeeded();

        if (!injector.getInstance(Dependencies.class).loadPlugins()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register every Bukkit listener bound via HiberniaModule.listeners(...) above.
        injector.getInstance(io.paradaux.hibernia.framework.events.ListenerManager.class).registerAll();
        MarketHook.init();

        // Optional: keep the shop registry clean when WorldEdit/FAWE bulk-removes
        // shops (WE bypasses Bukkit block events). Only touched when WE is present,
        // so its classes are never loaded otherwise.
        if (getServer().getPluginManager().getPlugin("WorldEdit") != null
                || getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            try {
                new io.paradaux.chestshop.find.integration.WorldEditShopCleanup(this).register();
            } catch (Throwable t) {
                getBukkitLogger().log(java.util.logging.Level.WARNING,
                        "WorldEdit shop-cleanup adapter unavailable", t);
            }
        }


    }

    public void loadConfig() {
        // Re-read config.yml through the framework. reload() repopulates the same
        // ChestShopConfiguration component instance in place, so the reference cached
        // here (and every injected reference elsewhere) reflects the new values.
        configurationLoader.reload();
        config = configurationLoader.getComponent(ChestShopConfiguration.class);

        accounts.load();

        if (handler != null) {
            shopLogger.removeHandler(handler);
        }

        if (config.isLogToFile()) {
            if (handler == null) {
                File log = loadFile("ChestShop.log");

                handler = loadHandler(log.getAbsolutePath());
                handler.setFormatter(new FileFormatter());
            }
            shopLogger.addHandler(handler);
        }

        shopLogger.setUseParentHandlers(config.isLogToConsole());
    }

    private void turnOffDatabaseLogging() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("");

        loggerConfig.addFilter(new AbstractFilter() {
            @Override
            public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, String msg, Object... params) {
                return filter(logger.getName(), level);
            }

            @Override
            public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, Object msg, Throwable t) {
                return filter(logger.getName(), level);
            }

            @Override
            public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, Message msg, Throwable t) {
                return filter(logger.getName(), level);
            }

            @Override
            public Result filter(LogEvent event) {
                return filter(event.getLoggerName(), event.getLevel());
            }

            private Result filter(String classname, Level level) {
                if (level.intLevel() <= Level.ERROR.intLevel() && !classname.contains("SqliteDatabaseType")) {
                    return Result.NEUTRAL;
                }

                if (classname.contains("SqliteDatabaseType") || classname.contains("TableUtils")) {
                    return Result.DENY;
                } else {
                    return Result.NEUTRAL;
                }
            }
        });
    }

    public static File loadFile(String string) {
        File file = new File(dataFolder, string);

        return loadFile(file);
    }

    private static File loadFile(File file) {
        if (!file.exists()) {
            try {
                if (file.getParent() != null) {
                    file.getParentFile().mkdirs();
                }

                file.createNewFile();
            } catch (IOException e) {
                getBukkitLogger().log(java.util.logging.Level.SEVERE, "Unable to load file " + file.getName(), e);
            }
        }

        return file;
    }

    private static FileHandler loadHandler(String path) {
        FileHandler handler = null;

        try {
            handler = new FileHandler(path, true);
        } catch (IOException ex) {
            getBukkitLogger().log(java.util.logging.Level.SEVERE, "Unable to load handler " + path, ex);
        }

        return handler;
    }

    public void onDisable() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        if (injector != null) {
            injector.getInstance(io.paradaux.chestshop.database.ChestShopDatabases.class).close();
        }

        if (handler != null) {
            handler.close();
            getLogger().removeHandler(handler);
        }
    }

    //////////////////    REGISTER EVENTS & SCHEDULER    ///////////////////////////
    public void registerEvent(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    ///////////////////////////////////////////////////////////////////////////////
    // The static ChestShop.<service>() locator is gone (PAR-282): every class takes its
    // collaborators through constructor DI. The plugin main class keeps private references
    // only for the few services it drives itself at enable time (migration, account load).

    public static File getFolder() {
        return dataFolder;
    }

    public static Logger getShopLogger() {
        return shopLogger;
    }

    public static Logger getBukkitLogger() {
        return logger;
    }

    public static void logDebug(String message) {
        if (plugin.config != null && plugin.config.isDebug()) {
            getBukkitLogger().info("[DEBUG] " + message);
        }
    }

    public static Server getBukkitServer() {
        return server;
    }

    public static String getVersion() {
        return description.getVersion();
    }

    public static String getPluginName() {
        return description.getName();
    }

    public static List<String> getDependencies() {
        return description.getSoftDepend();
    }

    public static ChestShop getPlugin() {
        return plugin;
    }


    public static <E extends Event> E callEvent(E event) {
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static void runInAsyncThread(Runnable runnable) {
        executorService.submit(runnable);
    }
}
