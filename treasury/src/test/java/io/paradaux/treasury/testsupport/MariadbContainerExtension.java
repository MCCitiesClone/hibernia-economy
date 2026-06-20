package io.paradaux.treasury.testsupport;

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
 * Two-mode MariaDB harness:
 *
 * <ol>
 *   <li><b>Embedded (default — local dev)</b>: boots MariaDB4j once per JVM and
 *       exposes a Hikari {@link DataSource}. No Docker, no system MariaDB needed.
 *       Schema loading goes through the bundled {@code mariadb} CLI so
 *       {@code DELIMITER} blocks parse correctly.</li>
 *   <li><b>External (CI)</b>: when {@code TREASURY_TEST_JDBC_URL} is set, point
 *       Hikari at that URL instead. The workflow is responsible for booting the
 *       service container and applying {@code schema.sql} via the mariadb CLI
 *       before tests run. MariaDB4j is skipped entirely — useful because its
 *       bundled binary needs system libs that newer Ubuntu runners don't ship.</li>
 * </ol>
 *
 * <p>Each test method gets a freshly truncated set of tables via
 * {@link #truncateAll(DataSource)} called from the integration test base.
 */
public final class MariadbContainerExtension {

    private static final String DATABASE_NAME = "treasury_test";

    /** When set, the harness uses this URL instead of booting an embedded DB. */
    private static final String EXTERNAL_URL_ENV  = "TREASURY_TEST_JDBC_URL";
    private static final String EXTERNAL_USER_ENV = "TREASURY_TEST_DB_USER";
    private static final String EXTERNAL_PASS_ENV = "TREASURY_TEST_DB_PASS";

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
        cfg.setMaximumPoolSize(4);
        cfg.setPoolName("treasury-tests-external");
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

            applySchema(dbConfig);

            String url = cfg.getURL(DATABASE_NAME);
            return new DbHolder(db, url);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start embedded MariaDB", e);
        }
    }

    /**
     * Loads the production {@code schema.sql} into the embedded DB via the bundled
     * {@code mysql} CLI client, which honours {@code DELIMITER} blocks. The triggers
     * defined under {@code DELIMITER //} update {@code account_balances_mat} after each
     * posting insert and are required for {@code LedgerService.transfer} to work.
     *
     * <p>One workaround applied: the {@code CREATE TABLE api_keys} block is stripped
     * because it foreign-keys to a {@code firm} table that lives in the sibling
     * {@code DemocracyCraft Business} plugin's schema. In production both plugins
     * apply their schemas to the same database, but the tests run Treasury in
     * isolation, so {@code api_keys} can't be created. No Treasury test exercises
     * that table.
     */
    private static void applySchema(DBConfiguration dbConfig) throws Exception {
        String raw;
        try (InputStream in = MariadbContainerExtension.class.getClassLoader()
                .getResourceAsStream("schema.sql")) {
            if (in == null) throw new IOException("schema.sql not on test classpath");
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String sql = stripApiKeysTable(raw);

        Path tempScript = Files.createTempFile("treasury-test-schema-", ".sql");
        tempScript.toFile().deleteOnExit();
        Files.writeString(tempScript, sql, StandardCharsets.UTF_8);

        runMysqlClient(dbConfig, tempScript.toFile());
    }

    /** Invokes the bundled {@code mysql} CLI binary with {@code --execute "SOURCE …"}. */
    private static void runMysqlClient(DBConfiguration dbConfig, File scriptFile) throws Exception {
        File binDir = new File(dbConfig.getBaseDir(), "bin");
        // Modern MariaDB renamed the CLI `mysql` -> `mariadb`; recent MariaDB4j
        // bundles only `mariadb`. Try both bundled names, then both system names,
        // so the harness works whether or not mariadb-client is installed.
        File mysql = firstExisting(
                new File(binDir, isWindows() ? "mysql.exe" : "mysql"),
                new File(binDir, isWindows() ? "mariadb.exe" : "mariadb"),
                isWindows() ? null : new File("/usr/bin/mysql"),
                isWindows() ? null : new File("/usr/bin/mariadb"));
        if (mysql == null) {
            throw new IOException("No mysql/mariadb client found in " + binDir
                    + " or /usr/bin — install mariadb-client");
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
            throw new IOException("mysql schema load exit=" + exit + ":\n" + output);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** First of the candidate files that exists, or null. Nulls are skipped. */
    private static File firstExisting(File... candidates) {
        for (File f : candidates) {
            if (f != null && f.isFile()) return f;
        }
        return null;
    }

    /** Removes the {@code CREATE TABLE api_keys} block (its FK to a missing {@code firm} table is broken). */
    private static String stripApiKeysTable(String sql) {
        int start = sql.indexOf("CREATE TABLE api_keys");
        if (start < 0) return sql;
        int end = sql.indexOf("COLLATE=utf8mb4_unicode_ci;", start);
        if (end < 0) return sql.substring(0, start);
        return sql.substring(0, start) + sql.substring(end + "COLLATE=utf8mb4_unicode_ci;".length());
    }

    private static HikariDataSource buildDataSource(String jdbcUrl) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername("root");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(4);
        cfg.setPoolName("treasury-tests");
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
            st.execute("TRUNCATE TABLE government_fines");
            st.execute("TRUNCATE TABLE account_redirects");
            st.execute("TRUNCATE TABLE player_login_times");
            st.execute("TRUNCATE TABLE account_group_authorizers");
            st.execute("TRUNCATE TABLE account_group_members");
            st.execute("TRUNCATE TABLE account_authorizers");
            st.execute("TRUNCATE TABLE account_members");
            st.execute("TRUNCATE TABLE account_balances_mat");
            st.execute("TRUNCATE TABLE ledger_postings");
            st.execute("TRUNCATE TABLE ledger_txns");
            st.execute("TRUNCATE TABLE accounts");
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
