package io.paradaux.treasury.guice.providers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class DataSourceProvider {
    private final HikariDataSource ds;

    public DataSourceProvider(String host, int port, String db, String user, String pass,
                              int maximumPoolSize, int minimumIdle,
                              long connectionTimeoutMs, long maxLifetimeMs, long keepaliveMs) {
        // Driver-side statement caching + batch rewrite. cachePrepStmts/useServerPrepStmts
        // let the connection reuse parsed statements; rewriteBatchedStatements turns the
        // multi-row posting insert into a single round-trip. utf8 for display names/memos.
        String jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=utf8"
                + "&useServerPrepStmts=true&cachePrepStmts=true"
                + "&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048"
                + "&rewriteBatchedStatements=true";
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(maximumPoolSize);
        cfg.setMinimumIdle(minimumIdle);
        cfg.setConnectionTimeout(connectionTimeoutMs);
        cfg.setMaxLifetime(maxLifetimeMs);
        cfg.setKeepaliveTime(keepaliveMs);
        cfg.setPoolName("Treasury-Hikari");
        cfg.setDriverClassName("org.mariadb.jdbc.Driver");
        cfg.setAutoCommit(false); // Let MyBatis/transactions manage commits
        this.ds = new HikariDataSource(cfg);
    }

    public DataSource get() { return ds; }

    public void close() { ds.close(); }
}
