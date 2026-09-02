package io.paradaux.jobs;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.paradaux.hibernia.framework.commander.CommandManager;
import io.paradaux.hibernia.framework.guice.HiberniaModule;
import io.paradaux.jobs.api.JobsApi;
import io.paradaux.jobs.commands.FireCommand;
import io.paradaux.jobs.commands.HireCommand;
import io.paradaux.jobs.commands.JobsCommands;
import io.paradaux.jobs.commands.LicenseListCommands;
import io.paradaux.jobs.commands.QualificationListCommands;
import io.paradaux.jobs.commands.QuitCommand;
import io.paradaux.jobs.commands.resolvers.JobArgResolver;
import io.paradaux.jobs.commands.resolvers.JobTypeArgResolver;
import io.paradaux.jobs.guice.DatabaseModule;
import io.paradaux.jobs.guice.JobsModule;
import io.paradaux.jobs.model.config.DatabaseConfiguration;
import io.paradaux.jobs.services.GroupProvisioner;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.tasks.JobReconciliationTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Job, licence and qualification management.
 *
 * <p>LuckPerms groups are the source of truth for who holds what. This plugin
 * declares which groups are job groups, who may hire into and fire from them, and
 * keeps an audit log plus a reconciled mirror in the shared economy database. Neither
 * table is authoritative: the mirror is rebuilt from LuckPerms by the reconciler, and
 * the log records transitions after the fact.</p>
 *
 * <p>LuckPerms is a soft dependency. Without it the plugin still enables, and every
 * membership read and write is refused with a clear message rather than failing at
 * class-load — see the {@code Class.forName} guard in {@code JobsModule}.</p>
 */
public class Jobs extends JavaPlugin {

    /**
     * Bare command roots that another plugin may already own. Whichever plugin
     * registers first wins the name, so these are conveniences and the canonical
     * forms live under {@code /jobs}.
     */
    private static final List<String> CONVENIENCE_ROOTS = List.of("hire", "fire", "quit", "resign");

    private Injector injector;

    @Override
    public void onEnable() {
        HiberniaModule hiberniaModule = HiberniaModule.forPlugin(this)
                .scanConfiguration("io.paradaux.jobs.model.config")
                .handlers(JobsCommands.class, HireCommand.class, FireCommand.class,
                        QuitCommand.class, LicenseListCommands.class,
                        QualificationListCommands.class)
                .resolvers(JobArgResolver.class, JobTypeArgResolver.class)
                .build();

        DatabaseConfiguration databaseConfig = hiberniaModule.configuration(DatabaseConfiguration.class);
        if (databaseConfig == null) {
            getLogger().severe("DatabaseConfiguration not found — check the config package scan. "
                    + "Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            injector = Guice.createInjector(hiberniaModule, new JobsModule(this),
                    new DatabaseModule(databaseConfig));
        } catch (RuntimeException e) {
            getLogger().severe("Failed to start: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        injector.getInstance(CommandManager.class).registerAll();
        warnOnCommandNameConflicts();
        registerApi();
        startBackgroundWork();

        getLogger().info("Jobs enabled.");
    }

    @Override
    public void onDisable() {
        // Unregister first so nothing reaches a half-torn-down plugin, then stop
        // tasks before anything they use goes away.
        getServer().getServicesManager().unregisterAll(this);
        getServer().getScheduler().cancelTasks(this);
        injector = null;
        getLogger().info("Jobs disabled.");
    }

    /**
     * Publish the API for other plugins — the primary path for job types another
     * plugin owns, such as trades awarded by a skills plugin.
     */
    private void registerApi() {
        JobsApi api = injector.getInstance(JobsApi.class);
        var existing = Bukkit.getServicesManager().getRegistration(JobsApi.class);
        if (existing != null) {
            Bukkit.getServicesManager().unregister(existing.getProvider());
        }
        Bukkit.getServicesManager().register(JobsApi.class, api, this, ServicePriority.Highest);
    }

    /**
     * Provision groups and schedule the reconciler, both gated on LuckPerms being
     * installed.
     *
     * <p>The presence check is by plugin name rather than by class, so no
     * {@code net.luckperms} type is referenced on a server without it.</p>
     */
    private void startBackgroundWork() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("LuckPerms is not installed — job membership is unavailable and "
                    + "every hire, fire and quit will be refused until it is.");
            return;
        }

        var snapshot = injector.getInstance(JobRegistry.class).snapshot();

        if (snapshot.provisionGroups()) {
            // Off the main thread: this is a storage round-trip per configured group.
            getServer().getScheduler().runTaskAsynchronously(this,
                    () -> injector.getInstance(GroupProvisioner.class).provisionAll());
        }

        var reconciliation = snapshot.reconciliation();
        if (reconciliation.enabled()) {
            injector.getInstance(JobReconciliationTask.class)
                    .schedule(reconciliation.intervalSeconds());
        }
    }

    /**
     * Warn when a convenience root resolved to a different plugin.
     *
     * <p>{@code /quit} in particular is a name other plugins take, and Brigadier
     * resolution is first-come — the symptom would otherwise be silent.</p>
     */
    private void warnOnCommandNameConflicts() {
        for (String root : CONVENIENCE_ROOTS) {
            var command = getServer().getCommandMap().getCommand(root);
            if (command == null) {
                continue;
            }
            String owner = command instanceof org.bukkit.command.PluginCommand pluginCommand
                    ? pluginCommand.getPlugin().getName() : null;
            if (owner != null && !getName().equals(owner)) {
                getLogger().warning("/" + root + " is owned by " + owner
                        + ", so the Jobs version is unavailable. Use /jobs " + root + " instead.");
            }
        }
    }
}
