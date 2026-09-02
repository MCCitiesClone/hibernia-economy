package io.paradaux.jobs.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.paradaux.jobs.Jobs;
import io.paradaux.jobs.permissions.LuckPermsBackend;
import io.paradaux.jobs.permissions.PermissionBackend;
import io.paradaux.jobs.permissions.UnavailablePermissionBackend;
import io.paradaux.jobs.api.JobsApi;
import io.paradaux.jobs.api.impl.JobsApiImpl;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.services.JobService;
import io.paradaux.jobs.services.impl.JobRegistryImpl;
import io.paradaux.jobs.services.impl.JobServiceImpl;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Domain wiring for the Jobs plugin.
 *
 * <p>{@code Plugin}, {@code ConfigurationLoader}, {@code Message} and the
 * command/resolver multibinders are bound by {@code HiberniaModule}; this module
 * must not re-bind any of them.</p>
 */
public final class JobsModule extends AbstractModule {

    private final Jobs plugin;

    public JobsModule(Jobs plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(JavaPlugin.class).toInstance(plugin);
        bind(Jobs.class).toInstance(plugin);

        bind(JobRegistry.class).to(JobRegistryImpl.class).in(Singleton.class);
        bind(JobService.class).to(JobServiceImpl.class).in(Singleton.class);
        bind(JobsApi.class).to(JobsApiImpl.class).in(Singleton.class);

        bindPermissionBackend();
    }

    /**
     * Bind the LuckPerms-backed permission backend when LuckPerms is installed, and
     * a no-op backend otherwise.
     *
     * <p>The {@code Class.forName} guard is the same one Treasury uses: without it
     * the bare {@code LuckPerms.class} literal throws {@code NoClassDefFoundError} at
     * enable on a server with no LuckPerms, taking the whole plugin down. Binding a
     * substitute rather than leaving the binding absent means every service can
     * depend on {@link PermissionBackend} unconditionally, with no optional injection
     * and no null checks — the plugin enables either way and simply refuses job
     * changes with a clear message.</p>
     */
    private void bindPermissionBackend() {
        if (isClassPresent("net.luckperms.api.LuckPerms")) {
            LuckPerms luckPerms = plugin.getServer().getServicesManager().load(LuckPerms.class);
            if (luckPerms != null) {
                bind(LuckPerms.class).toInstance(luckPerms);
                bind(PermissionBackend.class).to(LuckPermsBackend.class).in(Singleton.class);
                return;
            }
            plugin.getLogger().warning(
                    "LuckPerms is installed but has not registered its API service; "
                            + "job changes will be refused until it does.");
        }
        bind(PermissionBackend.class).to(UnavailablePermissionBackend.class).in(Singleton.class);
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
