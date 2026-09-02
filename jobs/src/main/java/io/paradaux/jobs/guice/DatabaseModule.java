package io.paradaux.jobs.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import io.paradaux.common.DataSourceProvider;
import io.paradaux.jobs.mappers.JobEventMapper;
import io.paradaux.jobs.mappers.JobMembershipMapper;
import io.paradaux.jobs.model.config.DatabaseConfiguration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.mybatis.guice.MyBatisModule;
import org.mybatis.guice.datasource.helper.JdbcHelper;

import javax.sql.DataSource;

/** MyBatis wiring for the two job tables in the shared economy database. */
public final class DatabaseModule extends AbstractModule {

    private final DatabaseConfiguration databaseConfig;

    @Inject
    public DatabaseModule(DatabaseConfiguration databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    @Override
    protected void configure() {
        install(new MyBatisModule() {
            @Override
            protected void initialize() {
                // MariaDB is MySQL-ish — the MySQL helper supplies dialect defaults.
                install(JdbcHelper.MySQL);
                bindTransactionFactoryType(JdbcTransactionFactory.class);

                addMapperClass(JobEventMapper.class);
                addMapperClass(JobMembershipMapper.class);

                environmentId("paper-mybatis");
                bindConstant().annotatedWith(
                                Names.named("mybatis.configuration.mapUnderscoreToCamelCase"))
                        .to(true);
            }
        });
    }

    @Provides
    @Singleton
    DataSource provideDataSource() {
        String host = databaseConfig.getHost();
        int port = Integer.parseInt(databaseConfig.getPort());
        String db = databaseConfig.getDatabase();
        String user = databaseConfig.getUsername();
        String pass = databaseConfig.getPassword();

        // Fail fast rather than silently booting against the shared money database
        // with a documented default password — the guard every writer to this
        // database carries (ADT-187).
        if ("password".equals(pass) || "CHANGE_ME".equals(pass)) {
            throw new IllegalStateException(
                    "Refusing to start: the database password is still the placeholder. "
                            + "Set database.password in the Jobs config.yml.");
        }

        return DataSourceProvider.builder(host, port, db, user, pass)
                .build()
                .get();
    }
}
