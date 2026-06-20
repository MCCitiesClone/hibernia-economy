package io.paradaux.business.testsupport;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfiguration;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Locale;

/**
 * Two-mode MariaDB harness, mirroring the sibling Treasury project.
 *
 * <ol>
 *   <li><b>Embedded (default — local dev)</b>: boots MariaDB4j once per JVM
 *       and exposes a Hikari {@link DataSource}. No Docker, no system MariaDB
 *       needed. Schema loading goes through the bundled {@code mysql} CLI so
 *       {@code DELIMITER} blocks (used by {@code uuid_to_bin}/{@code
 *       bin_to_uuid}) parse correctly. We apply {@code test-prelude.sql}
 *       (UUID helpers + stub {@code accounts} table) and then the production
 *       {@code sql/schema.sql}.</li>
 *   <li><b>External (CI)</b>: when {@code BUSINESS_TEST_JDBC_URL} is set,
 *       point Hikari at that URL instead. The workflow is responsible for
 *       booting the service container and applying {@code test-prelude.sql}
 *       + {@code schema.sql} via the {@code mariadb} CLI before tests run.
 *       MariaDB4j is skipped entirely — its bundled binary needs system libs
 *       that newer Ubuntu runners don't ship.</li>
 * </ol>
 */
public final class MariadbContainerExtension {

    private static final String DATABASE_NAME = "business_test";

    /** When set, the harness uses this URL instead of booting an embedded DB. */
    private static final String EXTERNAL_URL_ENV  = "BUSINESS_TEST_JDBC_URL";
    private static final String EXTERNAL_USER_ENV = "BUSINESS_TEST_DB_USER";
    private static final String EXTERNAL_PASS_ENV = "BUSINESS_TEST_DB_PASS";

    private static final Namespace NAMESPACE = Namespace.create(MariadbContainerExtension.class);
    private static final String DB_KEY = "embedded-db";
    private static final String DATASOURCE_KEY = "datasource";

    private MariadbContainerExtension() {}

    public static synchronized DataSource dataSource(ExtensionContext context) {
        Store store = context.getRoot().getStore(NAMESPACE);

        String externalUrl = System.getenv(EXTERNAL_URL_ENV);
        if (externalUrl != null && !externalUrl.isBlank()) {
            return store.getOrComputeIfAbsent(
                    DATASOURCE_KEY,
                    k -> buildExternalDataSource(externalUrl),
                    HikariDataSource.class);
        }

        DbHolder holder = store.getOrComputeIfAbsent(
                DB_KEY, k -> startEmbeddedDb(), DbHolder.class);
        return store.getOrComputeIfAbsent(
                DATASOURCE_KEY, k -> buildDataSource(holder.url), HikariDataSource.class);
    }

    private static HikariDataSource buildExternalDataSource(String jdbcUrl) {
        String user = System.getenv(EXTERNAL_USER_ENV);
        String pass = System.getenv(EXTERNAL_PASS_ENV);
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user != null ? user : "root");
        cfg.setPassword(pass != null ? pass : "");
        cfg.setMaximumPoolSize(8);
        cfg.setPoolName("business-tests-external");
        return new HikariDataSource(cfg);
    }

    private static DbHolder startEmbeddedDb() {
        try {
            DBConfigurationBuilder cfg = DBConfigurationBuilder.newBuilder();
            cfg.setPort(0); // dynamic free port
            DBConfiguration dbConfig = cfg.build();
            DB db = DB.newEmbeddedDB(dbConfig);
            db.start();
            db.createDB(DATABASE_NAME);

            applySql(dbConfig, "test-prelude.sql");
            applySql(dbConfig, "sql/schema.sql");

            String url = cfg.getURL(DATABASE_NAME);
            return new DbHolder(db, url);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start embedded MariaDB", e);
        }
    }

    private static void applySql(DBConfiguration dbConfig, String classpathResource) throws Exception {
        String sql;
        try (InputStream in = MariadbContainerExtension.class.getClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (in == null) throw new IOException(classpathResource + " not on test classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Path tempScript = Files.createTempFile("business-test-", ".sql");
        tempScript.toFile().deleteOnExit();
        Files.writeString(tempScript, sql, StandardCharsets.UTF_8);

        runMysqlClient(dbConfig, tempScript.toFile());
    }

    /** Invokes the bundled {@code mysql} CLI binary with {@code --execute "SOURCE …"}. */
    private static void runMysqlClient(DBConfiguration dbConfig, File scriptFile) throws Exception {
        File binDir = new File(dbConfig.getBaseDir(), "bin");
        File mysql = new File(binDir, isWindows() ? "mysql.exe" : "mysql");
        if (!mysql.isFile()) {
            // MariaDB4j ships a server binary but not always a client; fall
            // back to a system-installed mariadb-client (apt: mariadb-client)
            // which is wire-compatible.
            File systemMysql = isWindows() ? null : new File("/usr/bin/mysql");
            if (systemMysql != null && systemMysql.isFile()) {
                mysql = systemMysql;
            } else {
                throw new IOException("mysql client not found at " + mysql
                        + " and no system mariadb-client available — install mariadb-client");
            }
        }

        ProcessBuilder pb = new ProcessBuilder(
                mysql.getAbsolutePath(),
                "--protocol=TCP",
                "--port=" + dbConfig.getPort(),
                "--user=root",
                "--default-character-set=utf8",
                "--database=" + DATABASE_NAME,
                "--execute=SOURCE " + scriptFile.getAbsolutePath().replace("\\", "/")
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("mysql script load exit=" + exit + ":\n" + output);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static HikariDataSource buildDataSource(String jdbcUrl) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername("root");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(8);
        cfg.setPoolName("business-tests");
        return new HikariDataSource(cfg);
    }

    /** Holder keeps the embedded DB alive until JUnit closes the root context. */
    private static final class DbHolder implements ExtensionContext.Store.CloseableResource {
        final DB db;
        final String url;
        DbHolder(DB db, String url) { this.db = db; this.url = url; }
        @Override public void close() throws Exception { db.stop(); }
    }

    /** Truncates all data tables. Foreign-key checks disabled for the duration. */
    public static void truncateAll(DataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            st.execute("TRUNCATE TABLE firm_properties");
            st.execute("TRUNCATE TABLE firm_transfer_requests");
            st.execute("TRUNCATE TABLE firm_invites");
            st.execute("TRUNCATE TABLE firm_employee");
            st.execute("TRUNCATE TABLE firm_role_permission");
            st.execute("TRUNCATE TABLE firm_role");
            st.execute("TRUNCATE TABLE firm_accounts");
            st.execute("TRUNCATE TABLE firm");
            st.execute("TRUNCATE TABLE firm_players");
            st.execute("TRUNCATE TABLE accounts");
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
