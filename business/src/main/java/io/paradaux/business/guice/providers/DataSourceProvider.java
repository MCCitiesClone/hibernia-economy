package io.paradaux.business.guice.providers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class DataSourceProvider {
    private final HikariDataSource ds;

    public DataSourceProvider(String host, int port, String db, String user, String pass) {
        String jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + db + "?useUnicode=true&characterEncoding=utf8";
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setPoolName("Paper-Hikari");
        cfg.setDriverClassName("org.mariadb.jdbc.Driver");
        cfg.setAutoCommit(false); // Let MyBatis/transactions manage commits
        // READ COMMITTED (MariaDB default is REPEATABLE READ). Firm-account creation
        // reads firm_accounts, then calls treasury.createAccount() — a separate plugin
        // that commits on its own connection mid-transaction — then does
        // INSERT ... ON DUPLICATE KEY UPDATE on firm_accounts. Under REPEATABLE READ
        // that locking write fails with Error 1020 "Record has changed since last read"
        // because our read view is stale after the intervening commit. READ COMMITTED
        // gives each statement a fresh view. (Same fix treasury-ingest uses.)
        cfg.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        this.ds = new HikariDataSource(cfg);
    }

    public DataSource get() { return ds; }

    public void close() { ds.close(); }
}
