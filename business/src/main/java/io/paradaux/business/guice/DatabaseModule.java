package io.paradaux.business.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import io.paradaux.business.guice.providers.DataSourceProvider;
import io.paradaux.business.mappers.FirmAccountsMapper;
import io.paradaux.business.mappers.FirmAdminMapper;
import io.paradaux.business.mappers.FirmMapper;
import io.paradaux.business.mappers.FirmPlayerMapper;
import io.paradaux.business.mappers.FirmPropertyMapper;
import io.paradaux.business.mappers.FirmRequestMapper;
import io.paradaux.business.mappers.FirmRoleMapper;
import io.paradaux.business.mappers.FirmStaffMapper;
import io.paradaux.business.model.config.DatabaseConfiguration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.mybatis.guice.MyBatisModule;
import org.mybatis.guice.datasource.helper.JdbcHelper;

import javax.sql.DataSource;

public final class DatabaseModule extends AbstractModule {
    private final DatabaseConfiguration databaseConfig;

    @Inject
    public DatabaseModule(DatabaseConfiguration databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    @Override
    protected void configure() {
        // MyBatis module nested to keep things tidy
        install(new MyBatisModule() {
            @Override
            protected void initialize() {
                // MariaDB is MySQL-ish — use MySQL helper for dialect defaults
                install(JdbcHelper.MySQL);

                // Env name can be anything; we’re wiring our own DataSource
                bindTransactionFactoryType(JdbcTransactionFactory.class);
                // Let Guice inject our mappers
                addMapperClass(FirmMapper.class);
                addMapperClass(FirmAccountsMapper.class);
                addMapperClass(FirmRequestMapper.class);
                addMapperClass(FirmRoleMapper.class);
                addMapperClass(FirmStaffMapper.class);
                addMapperClass(FirmPlayerMapper.class);
                addMapperClass(FirmAdminMapper.class);
                addMapperClass(FirmPropertyMapper.class);

                // MyBatis settings (sane defaults)
                environmentId("paper-mybatis");
                bindConstant().annotatedWith(Names.named("mybatis.configuration.mapUnderscoreToCamelCase"))
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
        return new DataSourceProvider(host, port, db, user, pass).get();
    }
}
