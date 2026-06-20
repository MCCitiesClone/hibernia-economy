package io.paradaux.treasuryapi.guice.providers;

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
        this.ds = new HikariDataSource(cfg);
    }

    public DataSource get() { return ds; }

    public void close() { ds.close(); }
}
