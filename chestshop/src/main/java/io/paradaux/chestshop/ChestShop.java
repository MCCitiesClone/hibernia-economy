package io.paradaux.chestshop;

import io.paradaux.chestshop.configuration.ChestShopConfiguration;
import io.paradaux.chestshop.commands.Give;
import io.paradaux.chestshop.commands.ItemInfo;
import io.paradaux.chestshop.commands.ShopInfo;
import io.paradaux.chestshop.commands.Toggle;
import io.paradaux.chestshop.commands.Version;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.market.MarketHook;
import io.paradaux.chestshop.market.MarketListener;
import io.paradaux.chestshop.listeners.block.BlockPlace;
import io.paradaux.chestshop.listeners.block.breaking.ChestBreak;
import io.paradaux.chestshop.listeners.block.breaking.SignBreak;
import io.paradaux.chestshop.listeners.block.SignCreate;
import io.paradaux.chestshop.economy.EconomyProvider;
import io.paradaux.chestshop.listeners.GarbageTextListener;
import io.paradaux.chestshop.listeners.item.ItemMoveListener;
import io.paradaux.chestshop.listeners.modules.ItemAliasModule;
import io.paradaux.chestshop.listeners.modules.MetricsModule;
import io.paradaux.chestshop.listeners.modules.StockCounterModule;
import io.paradaux.chestshop.listeners.player.*;
import io.paradaux.chestshop.logging.FileFormatter;
import io.paradaux.chestshop.plugins.Dependencies;
import io.paradaux.chestshop.services.ItemCodeService;
import io.paradaux.chestshop.signs.RestrictedSign;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedBarChart;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.MultiLineChart;
import org.bstats.charts.SimpleBarChart;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    private static Metrics bStats;

    // Private references the plugin main class uses for its own enable-time orchestration
    // (DB migration, account-cache load, BungeeCord message rendering). NOT a service
    // locator — every other class injects its collaborators (PAR-282).
    private static io.paradaux.hibernia.framework.i18n.Message message;
    private static ItemCodeService itemCodes;
    private static io.paradaux.chestshop.services.AccountService accounts;

    private static File dataFolder;

    private static Logger logger;
    private static Logger shopLogger;
    private FileHandler handler;

    private com.google.inject.Injector injector;
    private io.paradaux.hibernia.framework.configurator.ConfigurationLoader configurationLoader;

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
        bStats = new Metrics(this, 1109);
        turnOffDatabaseLogging();

        // HiberniaFramework DI: loads ChestShopConfiguration from config.yml (and
        // additively reconciles new default keys on upgrade). The framework also
        // owns command registration now (PAR-264): the seven ChestShop commands
        // are CommandHandlers registered through Brigadier via CommandManager.
        // The framework Message bean is bound (no withoutMessages()) so both the
        // commander's error feedback and ChestShop's own command/listener output
        // render from messages.properties via ChestShop.message().
        io.paradaux.hibernia.framework.guice.HiberniaModule hibernia =
                io.paradaux.hibernia.framework.guice.HiberniaModule.forPlugin(this)
                        .scanConfiguration("io.paradaux.chestshop.configuration")
                        .handlers(io.paradaux.chestshop.commands.ChestShopCommand.class,
                                io.paradaux.chestshop.commands.BypassCommand.class,
                                ItemInfo.class, ShopInfo.class, Version.class,
                                io.paradaux.chestshop.commands.Metrics.class, Give.class,
                                Toggle.class)
                        // Bukkit entrypoints — Guice-constructed so each injects the
                        // services it needs (listener → service → persistence), and
                        // registered in one call via ListenerManager#registerAll (PAR-282).
                        .listeners(
                                Dependencies.class,
                                SignBreak.class,
                                io.paradaux.chestshop.listeners.block.breaking.attached.PhysicsBreak.class,
                                io.paradaux.chestshop.listeners.block.breaking.PaperBlockDestroyListener.class,
                                ChestBreak.class,
                                SignCreate.class,
                                BlockPlace.class,
                                io.paradaux.chestshop.listeners.block.SignBacksideProtector.class,
                                MarketListener.class,
                                PlayerConnect.class,
                                PlayerInteract.class,
                                PlayerInventory.class,
                                PlayerTeleport.class,
                                GarbageTextListener.class,
                                RestrictedSign.class,
                                StockCounterModule.class,
                                ItemMoveListener.class)
                        .build();
        this.injector = com.google.inject.Guice.createInjector(hibernia,
                new io.paradaux.chestshop.guice.ChestShopModule(),
                new io.paradaux.chestshop.guice.DatabaseModule(loadFile("users.db"), loadFile("items.db")));
        this.configurationLoader = injector.getInstance(
                io.paradaux.hibernia.framework.configurator.ConfigurationLoader.class);
        message = injector.getInstance(io.paradaux.hibernia.framework.i18n.Message.class);
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

        registerPluginMessagingChannels();

        startStatistics();
    }

    public void loadConfig() {
        // Re-read config.yml through the framework and refresh the static Properties
        // mirror. reload() re-fetches a fresh component snapshot (1.2.0 semantics).
        configurationLoader.reload();
        Properties.applyFrom(configurationLoader.getComponent(ChestShopConfiguration.class));

        accounts.load();

        if (handler != null) {
            shopLogger.removeHandler(handler);
        }

        if (Properties.LOG_TO_FILE) {
            if (handler == null) {
                File log = loadFile("ChestShop.log");

                handler = loadHandler(log.getAbsolutePath());
                handler.setFormatter(new FileFormatter());
            }
            shopLogger.addHandler(handler);
        }

        shopLogger.setUseParentHandlers(Properties.LOG_TO_CONSOLE);
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

    //////////////////    REGISTER EVENTS, SCHEDULER & STATS    ///////////////////////////
    private void registerPluginMessagingChannels() {
        if (Properties.BUNGEECORD_MESSAGES) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        }
    }

    public void registerEvent(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void startStatistics() {
        try (JarFile jarFile = new JarFile(this.getFile())) {
            String dist = jarFile.getManifest().getMainAttributes().getValue("Distribution-Type");
            bStats.addCustomChart(new SimplePie("distributionType", () -> dist));
        } catch (IOException ignored) {}

        String serverVersion = getServer().getBukkitVersion().split("-")[0];
        bStats.addCustomChart(createStaticDrilldownStat("versionMcSelf", serverVersion, getDescription().getVersion()));
        bStats.addCustomChart(createStaticDrilldownStat("versionSelfMc", getDescription().getVersion(), serverVersion));

        bStats.addCustomChart(createStaticDrilldownStat("serverTypeVersionSelf", getServer().getName(), getDescription().getVersion()));
        bStats.addCustomChart(createStaticDrilldownStat("versionSelfServerType", getDescription().getVersion(), getServer().getName()));

        bStats.addCustomChart(createStaticDrilldownStat("versionMcServerType", serverVersion, getServer().getName()));
        bStats.addCustomChart(createStaticDrilldownStat("serverTypeVersionMc", getServer().getName(), serverVersion));

        String javaVersion = System.getProperty("java.version");
        bStats.addCustomChart(createStaticDrilldownStat("versionJavaSelf", javaVersion, getDescription().getVersion()));
        bStats.addCustomChart(createStaticDrilldownStat("versionSelfJava", getDescription().getVersion(), javaVersion));

        bStats.addCustomChart(createStaticDrilldownStat("versionJavaMc", javaVersion, serverVersion));
        bStats.addCustomChart(createStaticDrilldownStat("versionMcJava", serverVersion, javaVersion));

        bStats.addCustomChart(new SingleLineChart("shopAccounts", () -> accounts.getAccountCount()));
        bStats.addCustomChart(new MultiLineChart("transactionCount", () -> ImmutableMap.of(
                "total", MetricsModule.getTotalTransactions(),
                "buy", MetricsModule.getBuyTransactions(),
                "sell", MetricsModule.getSellTransactions()
        )));
        bStats.addCustomChart(new MultiLineChart("itemCount", () -> ImmutableMap.of(
                "total", MetricsModule.getTotalItemsCount(),
                "buy", MetricsModule.getSoldItemsCount(),
                "sell", MetricsModule.getBoughtItemsCount()
        )));

        bStats.addCustomChart(new SimplePie("includeSettingsInMetrics", () -> Properties.INCLUDE_SETTINGS_IN_METRICS ? "enabled" : "disabled"));
        if (!Properties.INCLUDE_SETTINGS_IN_METRICS) return;

        bStats.addCustomChart(new SimplePie("ensure-correct-playerid", () -> Properties.ENSURE_CORRECT_PLAYERID ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("allow-sign-chest-open", () -> Properties.ALLOW_SIGN_CHEST_OPEN ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("uses-server-economy-account", () -> !Properties.SERVER_ECONOMY_ACCOUNT.isEmpty() ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("uses-server-economy-account-uuid", () -> !Properties.SERVER_ECONOMY_ACCOUNT_UUID.equals(new UUID(0, 0)) ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("allow-partial-transactions", () -> Properties.ALLOW_PARTIAL_TRANSACTIONS ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("bungeecord-messages", () -> Properties.BUNGEECORD_MESSAGES ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("allow-multiple-shops-at-one-block", () -> Properties.ALLOW_MULTIPLE_SHOPS_AT_ONE_BLOCK ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("allow-partial-transactions", () -> Properties.ALLOW_PARTIAL_TRANSACTIONS ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("log-to-console", () -> Properties.LOG_TO_CONSOLE ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("log-to-file", () -> Properties.LOG_TO_FILE ? "enabled" : "disabled"));

        bStats.addCustomChart(new AdvancedBarChart("pluginProperties", () -> {
            Map<String, int[]> map = new LinkedHashMap<>();
            map.put("ensure-correct-playerid", getChartArray(Properties.ENSURE_CORRECT_PLAYERID));
            map.put("reverse-buttons", getChartArray(Properties.REVERSE_BUTTONS));
            map.put("shift-sells-in-stacks", getChartArray(Properties.SHIFT_SELLS_IN_STACKS));
            map.put("shift-sells-everything", getChartArray(Properties.SHIFT_SELLS_EVERYTHING));
            map.put("allow-sign-chest-open", getChartArray(!Properties.ALLOW_SIGN_CHEST_OPEN));
            map.put("sign-dying", getChartArray(!Properties.SIGN_DYING));
            map.put("remove-empty-shops", getChartArray(!Properties.REMOVE_EMPTY_SHOPS));
            map.put("remove-empty-chests", getChartArray(!Properties.REMOVE_EMPTY_CHESTS));
            map.put("uses-server-economy-account", getChartArray(!Properties.SERVER_ECONOMY_ACCOUNT.isEmpty()));
            map.put("uses-server-economy-account-uuid", getChartArray(!Properties.SERVER_ECONOMY_ACCOUNT_UUID.equals(new UUID(0, 0))));
            map.put("allow-multiple-shops-at-one-block", getChartArray(Properties.ALLOW_MULTIPLE_SHOPS_AT_ONE_BLOCK));
            map.put("allow-partial-transactions", getChartArray(Properties.ALLOW_PARTIAL_TRANSACTIONS));
            map.put("bungeecord-messages", getChartArray(Properties.BUNGEECORD_MESSAGES));
            map.put("log-to-console", getChartArray(Properties.LOG_TO_CONSOLE));
            map.put("log-to-file", getChartArray(Properties.LOG_TO_FILE));
            return map;
        }));
        bStats.addCustomChart(new SimpleBarChart("shopContainers",
                () -> Properties.SHOP_CONTAINERS.stream().map(Material::name).collect(Collectors.toMap(k -> k, k -> 1))));
    }

    public static DrilldownPie createStaticDrilldownStat(String statId, String value1, String value2) {
        final Map<String, Map<String, Integer>> map = ImmutableMap.of(value1, ImmutableMap.of(value2, 1));
        return new DrilldownPie(statId, () -> map);
    }

    public static DrilldownPie createStaticDrilldownStat(String statId, Callable<EconomyProvider.ProviderInfo> callableProviderInfo) {
        return new DrilldownPie(statId, () -> {
            EconomyProvider.ProviderInfo providerInfo = callableProviderInfo.call();
            if (providerInfo == null) {
                return ImmutableMap.of();
            }
            return ImmutableMap.of(providerInfo.getName(), ImmutableMap.of(providerInfo.getVersion(), 1));
        });
    }

    private int[] getChartArray(boolean value) {
        return new int[]{!value ? 1 : 0, value ? 0 : 1};
    }

    ///////////////////////////////////////////////////////////////////////////////
    // The static ChestShop.<service>() locator is gone (PAR-282): every class takes its
    // collaborators through constructor DI. The plugin main class keeps private references
    // only for the few services it drives itself at enable time (migration, account load,
    // BungeeCord message rendering).

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
        if (Properties.DEBUG) {
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

    public static Metrics getMetrics() {
        return bStats;
    }

    /**
     * Build a placeholder-value map for the framework {@link io.paradaux.hibernia.framework.i18n.Message}:
     * a base map plus key/value replacement pairs, optionally blanking {@code {prefix}}. Every ChestShop
     * template begins with {@code {prefix}}, so {@code withPrefix=false} suppresses it (continuation lines).
     */
    public static Map<String, Object> values(boolean withPrefix, Map<String, String> base, String... replacements) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!withPrefix) {
            values.put("prefix", "");
        }
        if (base != null) {
            values.putAll(base);
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            values.put(replacements[i], replacements[i + 1]);
        }
        return values;
    }

    public static <E extends Event> E callEvent(E event) {
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static void sendBungeeMessage(String playerName, String key, Map<String, String> replacementMap, String... replacements) {
        sendBungeeMessage(playerName, message.component(key, values(true, replacementMap, replacements)));
    }

    public static void sendBungeeMessage(String playerName, String message) {
        sendBungeeMessage(playerName, "Message", message);
    }

    public static void sendBungeeMessage(String playerName, BaseComponent[] message) {
        sendBungeeMessage(playerName, "MessageRaw", ComponentSerializer.toString(message));
    }

    public static void sendBungeeMessage(String playerName, Component message) {
        sendBungeeMessage(playerName, "MessageRaw", GsonComponentSerializer.gson().serialize(message));
    }

    private static void sendBungeeMessage(String playerName, String channel, String message) {
        if (Properties.BUNGEECORD_MESSAGES && !Bukkit.getOnlinePlayers().isEmpty()) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(channel);
            out.writeUTF(playerName);
            out.writeUTF(message);

            Bukkit.getOnlinePlayers().iterator().next().sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }
    }

    public static void runInAsyncThread(Runnable runnable) {
        executorService.submit(runnable);
    }
}
