package io.paradaux.treasury.testsupport;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.paradaux.treasury.guice.DatabaseModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.sql.DataSource;

/**
 * Base class for integration tests that hit a real MariaDB via Testcontainers.
 *
 * <p>The container is started once per JVM and shared across every subclass.
 * Each test method gets a clean slate via {@link MariadbContainerExtension#truncateAll}.
 *
 * <p>Subclasses receive a fully-wired Guice {@link Injector} with the production
 * service implementations and {@link TestServicesModule} bindings for configurations
 * and stubbed out-of-process collaborators.
 */
@Tag("integration")
public abstract class IntegrationTestBase {

    @RegisterExtension
    static final ContextHolder CONTEXT = new ContextHolder();

    protected DataSource dataSource;
    protected Injector injector;

    @BeforeEach
    void setUpIntegration() throws Exception {
        this.dataSource = MariadbContainerExtension.dataSource(CONTEXT.context);
        MariadbContainerExtension.truncateAll(dataSource);
        this.injector = Guice.createInjector(
                new DatabaseModule(dataSource),
                new TestServicesModule()
        );
    }

    /** Captures an ExtensionContext so the static container helper can access JUnit's store. */
    static final class ContextHolder
            implements org.junit.jupiter.api.extension.BeforeAllCallback {
        ExtensionContext context;
        @Override public void beforeAll(ExtensionContext ctx) {
            this.context = ctx;
        }
    }
}
