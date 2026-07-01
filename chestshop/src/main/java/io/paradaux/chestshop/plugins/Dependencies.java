package io.paradaux.chestshop.plugins;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.services.EconomyService;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.chestshop.services.ProtectionService;
import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.model.config.ChestShopConfiguration;
import io.paradaux.chestshop.economy.EconomyProvider;
import io.paradaux.chestshop.economy.TreasuryEconomyProvider;
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
@Singleton
public class Dependencies implements Listener {

    private final Map<String, String> versions = new HashMap<>();

    private final ProtectionService protection;
    private final ItemService items;
    private final EconomyService economy;
    private final ChestShopConfiguration config;

    @Inject
    public Dependencies(ProtectionService protection, ItemService items, EconomyService economy, ChestShopConfiguration config) {
        this.protection = protection;
        this.items = items;
        this.economy = economy;
        this.config = config;
    }

    private boolean isLoaded(String plugin) {
        return versions.containsKey(plugin.toLowerCase(Locale.ROOT));
    }

    // Runs in onLoad() (before the Guice injector exists) so it stays static — it
    // forces dependency static-init (e.g. WorldGuard flag registration) and touches
    // no injected services.
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

    public boolean loadPlugins() {
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

    private boolean loadEconomy() {
        String plugin = "none";

        EconomyProvider economyProvider = null;

        if (Bukkit.getPluginManager().getPlugin("Treasury") != null) {
            plugin = "Treasury";
            economyProvider = TreasuryEconomyProvider.prepare(economy);
        }

        if (economyProvider == null) {
            ChestShop.getBukkitLogger().severe("No Economy provider found! You need to install Treasury!");
            return false;
        }

        ChestShop.getMetrics().addCustomChart(ChestShop.createStaticDrilldownStat("economyAdapter", plugin, Bukkit.getPluginManager().getPlugin(plugin).getDescription().getVersion()));
        ChestShop.getMetrics().addCustomChart(ChestShop.createStaticDrilldownStat("economyPlugin", economyProvider::getProviderInfo));

        ChestShop.getBukkitLogger().info(plugin + " loaded!");
        return true;
    }

    public boolean loadPlugin(String name, Plugin plugin) { //Really messy, right? But it's short and fast :)
        if (isLoaded(name) || isLoaded(plugin.getName())) {
            return true;
        }
        Dependency dependency;

        try {
            dependency = Dependency.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        switch (dependency) {
            //Terrain protection plugins
            case WorldGuard:
                boolean inUse = config.isWorldguardUseProtection() || config.isWorldguardIntegration();

                if (!inUse) {
                    return false;
                }

                if (config.isWorldguardUseProtection()) {
                    protection.setWorldGuardProtection(new WorldGuardProtection(plugin)::onProtectionCheck);
                }

                if (config.isWorldguardIntegration()) {
                    protection.setWorldGuardBuilding(new WorldGuardBuilding(plugin, config)::canBuild);
                }

                break;

            case GriefPrevention:
                if (!config.isGriefpreventionIntegration()) {
                    return false;
                }
                protection.setGriefPreventionBuilding(new GriefPrevenentionBuilding(plugin)::canBuild);
                break;

            //Other plugins
            case ItemBridge:
                // ItemBridge's resolvers are invoked directly by ItemService; flag the
                // integration as available rather than registering a listener. This keeps
                // the com.jojodmo.itembridge classes off the path unless the plugin is here.
                items.enableItemBridge();
                break;
            case Nexo:
                // Native Nexo/ItemsAdder custom-item support (ported from NexoUtilities).
                // Like ItemBridge, its resolvers are invoked directly by ItemService, keeping
                // the com.nexomc.nexo classes off the path unless the plugin is here.
                items.enableNexo();
                break;
            case ShowItem:
                MaterialUtil.Show.initialize(plugin);
                break;
        }

        PluginDescriptionFile description = plugin.getDescription();
        versions.put(description.getName(), description.getVersion());
        ChestShop.getBukkitLogger().info(description.getName() + " version " + description.getVersion() + " hooked.");

        return true;
    }

    private enum Dependency {
        WorldGuard,
        GriefPrevention,

        ItemBridge,
        Nexo,

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
