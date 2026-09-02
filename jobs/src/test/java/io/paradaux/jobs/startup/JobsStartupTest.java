package io.paradaux.jobs.startup;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import io.paradaux.hibernia.framework.guice.HiberniaModule;
import io.paradaux.hibernia.testsupport.HiberniaStartupAssertion;
import io.paradaux.jobs.Jobs;
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
import io.paradaux.jobs.permissions.PermissionBackend;
import io.paradaux.jobs.permissions.UnavailablePermissionBackend;
import io.paradaux.jobs.services.JobRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Builds the plugin's <em>real</em> Guice graph over a live MockBukkit server and
 * drives {@code registerAll()} the way {@link Jobs#onEnable()} does, so missing
 * bindings and route conflicts across the six command roots surface here rather than
 * at server boot.
 *
 * <p>The plugin is loaded but not enabled, so the production {@code onEnable} — which
 * opens a MariaDB pool — never runs; the real {@link DatabaseModule}'s
 * {@code @Provides DataSource} is overridden with a non-connecting mock, since
 * building MyBatis mapper proxies never touches the database.</p>
 *
 * <p>LuckPerms is absent under MockBukkit, which makes this test doubly useful: it
 * also proves the degraded path a server without LuckPerms actually boots into, where
 * {@link UnavailablePermissionBackend} is bound and nothing resolves a
 * {@code net.luckperms} class.</p>
 */
class JobsStartupTest {

    private ServerMock server;
    private Jobs plugin;

    @BeforeEach
    void boot() {
        server = MockBukkit.mock();
        plugin = (Jobs) server.getPluginManager().loadPlugin(Jobs.class);
    }

    @AfterEach
    void shutdown() {
        MockBukkit.unmock();
    }

    private Injector realInjector() {
        // The exact HiberniaModule wiring Jobs.onEnable() builds.
        HiberniaModule hibernia = HiberniaModule.forPlugin(plugin)
                .scanConfiguration("io.paradaux.jobs.model.config")
                .handlers(JobsCommands.class, HireCommand.class, FireCommand.class,
                        QuitCommand.class, LicenseListCommands.class,
                        QualificationListCommands.class)
                .resolvers(JobArgResolver.class, JobTypeArgResolver.class)
                .build();

        DatabaseConfiguration databaseConfig = hibernia.configuration(DatabaseConfiguration.class);
        assertThat(databaseConfig).as("DatabaseConfiguration must be discovered by the package scan")
                .isNotNull();

        return Guice.createInjector(
                hibernia,
                new JobsModule(plugin),
                Modules.override(new DatabaseModule(databaseConfig)).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(DataSource.class).toInstance(mock(DataSource.class));
                    }
                }));
    }

    @Test
    void realInjectorRegistersEveryCommandAndResolver() {
        HiberniaStartupAssertion.assertRegisters(realInjector());
    }

    @Test
    void theApiIsConstructibleFromTheRealGraph() {
        // What Jobs.onEnable() hands to the services manager for other plugins.
        assertThat(realInjector().getInstance(JobsApi.class)).isNotNull();
    }

    @Test
    void withoutLuckPermsTheUnavailableBackendIsBound() {
        // MockBukkit has no LuckPerms, which is exactly the degraded production path:
        // the plugin still builds, and no net.luckperms class is ever resolved.
        PermissionBackend backend = realInjector().getInstance(PermissionBackend.class);

        assertThat(backend).isInstanceOf(UnavailablePermissionBackend.class);
        assertThat(backend.available()).isFalse();
    }

    @Test
    void theShippedJobsYmlLoadsIntoTheRegistry() {
        // saveResource copies jobs.yml into the data folder on first construction, so
        // this also proves the shipped file parses through the real code path.
        JobRegistry registry = realInjector().getInstance(JobRegistry.class);

        assertThat(registry.snapshot().types()).extracting(t -> t.key())
                .contains("government", "trades", "licenses", "qualifications");
        assertThat(registry.snapshot().listingType("licenses")).contains("licenses");
        assertThat(registry.snapshot().listingType("qualifications")).contains("qualifications");
    }
}
