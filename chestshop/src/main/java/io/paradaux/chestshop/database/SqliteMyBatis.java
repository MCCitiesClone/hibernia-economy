package io.paradaux.chestshop.database;

import io.paradaux.chestshop.mappers.typehandlers.DateLongTypeHandler;
import io.paradaux.chestshop.mappers.typehandlers.UuidStringTypeHandler;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens one MyBatis store over a single SQLite database file. ChestShop keeps its two
 * stores ({@code users.db}, {@code items.db}) in separate files, so each gets its own
 * {@link SqlSessionManager} here rather than the single shared-MariaDB
 * {@code MyBatisModule} the other plugins use (PAR-282 — MyBatis, not MariaDB).
 *
 * <p>The {@link SqlSessionManager} auto-manages a session per mapper call and commits it,
 * so an injected mapper "just works" without a transaction interceptor (mirroring how
 * mybatis-guice behaves on the other plugins). The pool is capped at a single connection
 * so concurrent main-thread/async access to the same SQLite file is serialised — SQLite
 * is single-writer — exactly as the old single ORMlite {@code ConnectionSource} was.
 */
public final class SqliteMyBatis {

    private final PooledDataSource dataSource;
    private final SqlSessionManager sessionManager;

    private SqliteMyBatis(PooledDataSource dataSource, SqlSessionManager sessionManager) {
        this.dataSource = dataSource;
        this.sessionManager = sessionManager;
    }

    /**
     * Open the store for {@code dbFile}, registering {@code mapperClass}, running the
     * {@code requiredDdl} (idempotent CREATE statements) and then the best-effort
     * {@code optionalDdl} (e.g. {@code ALTER TABLE ADD COLUMN} for a pre-existing older
     * schema — failures, such as a column that already exists, are ignored).
     */
    public static SqliteMyBatis open(File dbFile, Class<?> mapperClass, String[] requiredDdl, String[] optionalDdl) {
        PooledDataSource dataSource = new PooledDataSource(
                "org.sqlite.JDBC", "jdbc:sqlite:" + dbFile.getAbsolutePath(), null, null);
        // SQLite is single-writer; one connection serialises all access to this file.
        dataSource.setPoolMaximumActiveConnections(1);
        dataSource.setPoolMaximumIdleConnections(1);

        runDdl(dataSource, dbFile, requiredDdl, optionalDdl);

        Configuration configuration = new Configuration(
                new Environment("chestshop-sqlite", new JdbcTransactionFactory(), dataSource));
        configuration.getTypeHandlerRegistry().register(new UuidStringTypeHandler());
        configuration.getTypeHandlerRegistry().register(new DateLongTypeHandler());
        configuration.addMapper(mapperClass);

        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        return new SqliteMyBatis(dataSource, SqlSessionManager.newInstance(factory));
    }

    private static void runDdl(PooledDataSource dataSource, File dbFile, String[] requiredDdl, String[] optionalDdl) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : requiredDdl) {
                statement.execute(sql);
            }
            for (String sql : optionalDdl) {
                try {
                    statement.execute(sql);
                } catch (SQLException ignored) {
                    // Best-effort schema top-up (e.g. ADD COLUMN on a DB that already has it).
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialise the ChestShop database " + dbFile, e);
        }
    }

    /** The auto-committing mapper proxy to inject into services. */
    public <M> M getMapper(Class<M> mapperClass) {
        return sessionManager.getMapper(mapperClass);
    }

    /** Close the underlying connection pool (call from the plugin's {@code onDisable}). */
    public void close() {
        dataSource.forceCloseAll();
    }
}
