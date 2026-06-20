package io.paradaux.treasuryapi.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import io.paradaux.treasuryapi.guice.providers.DataSourceProvider;
import io.paradaux.treasuryapi.mappers.ApiKeyMapper;
import io.paradaux.treasuryapi.mappers.ExplorerGroupMapper;
import io.paradaux.treasuryapi.mappers.ExplorerUiMapper;
import io.paradaux.treasuryapi.mappers.typehandlers.UuidBinaryTypeHandler;
import io.paradaux.treasuryapi.model.config.DatabaseConfiguration;
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
        install(new MyBatisModule() {
            @Override
            protected void initialize() {
                // MariaDB is MySQL-ish — use MySQL helper for dialect defaults
                install(JdbcHelper.MySQL);

                bindTransactionFactoryType(JdbcTransactionFactory.class);

                addMapperClass(ApiKeyMapper.class);
                addMapperClass(ExplorerUiMapper.class);
                addMapperClass(ExplorerGroupMapper.class);

                addTypeHandlerClass(UuidBinaryTypeHandler.class);

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
