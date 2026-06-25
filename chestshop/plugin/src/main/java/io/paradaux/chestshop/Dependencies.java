package io.paradaux.chestshop;

import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.listeners.economy.EconomyAdapter;
import io.paradaux.chestshop.listeners.economy.plugins.TreasuryListener;
import io.paradaux.chestshop.plugins.*;
import com.google.common.collect.ImmutableMap;
import org.bstats.charts.DrilldownPie;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * @author Acrobot
 */
public class Dependencies implements Listener {

    private static final Map<String, String> versions = new HashMap<>();

    private static boolean isLoaded(String plugin) {
        return versions.containsKey(plugin.toLowerCase(Locale.ROOT));
    }

    public static void initializePlugins() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        for (String dependency : ChestShop.getDependencies()) {
            Plugin plugin = pluginManager.getPlugin(dependency);

            if (plugin != null) {
                initializePlugin(dependency, plugin);
            }
        }
    }

    private static void initializePlugin(String name, Plugin plugin) { //Really messy, right? But it's short and fast :)
        Dependency dependency;

        try {
            dependency = Dependency.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return;
        }

        switch (dependency) {
            //Terrain protection plugins
            case WorldGuard:
                WorldGuardFlags.ENABLE_SHOP.getName();  // force the static code to run
                break;
        }

        PluginDescriptionFile description = plugin.getDescription();
        ChestShop.getBukkitLogger().info(description.getName() + " version " + description.getVersion() + " loaded.");
    }

    public static boolean loadPlugins() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        for (Dependency dependency : Dependency.values()) {
            Plugin plugin = pluginManager.getPlugin(dependency.name());

            if (plugin != null && plugin.isEnabled()) {
                try {
                    loadPlugin(dependency.name(), plugin);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Unable to hook into " + plugin.getName() + " " + plugin.getDescription().getVersion(), e);
                }
            }
        }

        if (loadEconomy()) {
            Map<String, Map<String, Integer>> map = versions.entrySet().stream()
                    .map(e -> new AbstractMap.SimpleEntry<String, Map<String, Integer>>(e.getKey(), ImmutableMap.of(e.getValue(), 1)))
                    .collect(Collectors.toMap(
                            AbstractMap.SimpleEntry::getKey,
                            AbstractMap.SimpleEntry::getValue
                    ));
            ChestShop.getMetrics().addCustomChart(new DrilldownPie("dependencies", () -> map));
            return true;
        }
        return false;
    }

    private static boolean loadEconomy() {
        String plugin = "none";

        EconomyAdapter economy = null;

        if (Bukkit.getPluginManager().getPlugin("Treasury") != null) {
            plugin = "Treasury";
            economy = TreasuryListener.prepareListener();
        }

        if (economy == null) {
            ChestShop.getBukkitLogger().severe("No Economy adapter found! You need to install Treasury!");
            return false;
        }

        ChestShop.getMetrics().addCustomChart(ChestShop.createStaticDrilldownStat("economyAdapter", plugin, Bukkit.getPluginManager().getPlugin(plugin).getDescription().getVersion()));
        ChestShop.getMetrics().addCustomChart(ChestShop.createStaticDrilldownStat("economyPlugin", economy::getProviderInfo));

        ChestShop.registerListener(economy);
        ChestShop.getBukkitLogger().info(plugin + " loaded!");
        return true;
    }

    public static boolean loadPlugin(String name, Plugin plugin) { //Really messy, right? But it's short and fast :)
        if (isLoaded(name) || isLoaded(plugin.getName())) {
            return true;
        }
        Dependency dependency;

        try {
            dependency = Dependency.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        Listener listener = null;

        switch (dependency) {
            //Protection plugins
            case LWC:
                listener = new LightweightChestProtection();
                break;
            case LockettePro:
                listener = new LockettePro();
                break;
            case BlockLocker:
                listener = new BlockLocker();
                break;

            //Terrain protection plugins
            case WorldGuard:
                boolean inUse = Properties.WORLDGUARD_USE_PROTECTION || Properties.WORLDGUARD_INTEGRATION;

                if (!inUse) {
                    return false;
                }

                if (Properties.WORLDGUARD_USE_PROTECTION) {
                    ChestShop.registerListener(new WorldGuardProtection(plugin));
                }

                if (Properties.WORLDGUARD_INTEGRATION) {
                    listener = new WorldGuardBuilding(plugin);
                }

                break;

            case GriefPrevention:
                if (!Properties.GRIEFPREVENTION_INTEGRATION) {
                    return false;
                }
                listener = new GriefPrevenentionBuilding(plugin);
                break;

            case RedProtect:
                if (!Properties.REDPROTECT_INTEGRATION) {
                    return false;
                }
                listener = new RedProtectBuilding(plugin);
                break;

            //Other plugins
            case AuthMe:
                listener = new AuthMe();
                break;
            case Heroes:
                Heroes heroes = Heroes.getHeroes(plugin);

                if (heroes == null) {
                    return false;
                }

                listener = heroes;
                break;
            case ItemBridge:
                listener = new ItemBridge();
                break;
            case ShowItem:
                MaterialUtil.Show.initialize(plugin);
                break;
        }

        if (listener != null) {
            ChestShop.registerListener(listener);
        }

        PluginDescriptionFile description = plugin.getDescription();
        versions.put(description.getName(), description.getVersion());
        ChestShop.getBukkitLogger().info(description.getName() + " version " + description.getVersion() + " hooked.");

        return true;
    }

    private enum Dependency {
        LWC,
        LockettePro,
        BlockLocker,

        WorldGuard,
        GriefPrevention,
        RedProtect,

        AuthMe,

        Heroes,

        ItemBridge,

        ShowItem
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnable(PluginEnableEvent event) {
        Plugin plugin = event.getPlugin();
        try {
            loadPlugin(plugin.getName(), plugin);
            // Also try the plugin's declared provided aliases (getProvides()), so
            // a dependency we support under a different "provides" name still hooks.
            for (String pluginAlias : plugin.getDescription().getProvides()) {
                if (loadPlugin(pluginAlias, plugin)) {
                    break;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Unable to hook into " + plugin.getName() + " " + plugin.getDescription().getVersion(), e);
        }
    }
}
