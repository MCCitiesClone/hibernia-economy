package io.paradaux.treasuryrestapi.testsupport;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

/**
 * One embedded MariaDB (MariaDB4j) for the whole JVM, used by the DB-backed
 * integration tests. Boots lazily on first {@link #ensureStarted()}, creates the
 * subset of tables the API touches, and applies the schema over plain JDBC (no
 * {@code mysql}/{@code mariadb} CLI needed — these DDLs have no triggers).
 *
 * <p>Covers the ledger write side ({@code accounts}, {@code account_balances_mat},
 * {@code ledger_txns}, {@code ledger_postings}) plus {@code firm}/{@code firm_players}
 * for type-aware owner/recipient resolution.
 */
public final class EmbeddedMariaDb {

    private static final String DB_NAME = "treasury_rest_test";
    private static volatile String jdbcUrl;
    private static DB db;

    private EmbeddedMariaDb() {}

    public static synchronized void ensureStarted() {
        if (jdbcUrl != null) return;
        try {
            DBConfigurationBuilder cfg = DBConfigurationBuilder.newBuilder();
            cfg.setPort(0); // free port
            db = DB.newEmbeddedDB(cfg.build());
            db.start();
            db.createDB(DB_NAME);
            jdbcUrl = cfg.getURL(DB_NAME);
            applySchema();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start embedded MariaDB", e);
        }
    }

    public static String jdbcUrl() { ensureStarted(); return jdbcUrl; }
    public static String username() { return "root"; }
    public static String password() { return ""; }

    public static Connection connect() throws Exception {
        ensureStarted();
        return DriverManager.getConnection(jdbcUrl, "root", "");
    }

    /** Truncate the tables between tests for isolation. */
    public static void truncateAll() {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String t : new String[]{
                    "accounts", "firm", "firm_accounts", "firm_players", "account_balances_mat",
                    "ledger_txns", "ledger_postings", "chestshop_sale", "chestshop_shop"}) {
                st.execute("TRUNCATE TABLE " + t);
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** UUID -> BINARY(16) bytes, matching the production UuidTypeHandler. */
    public static byte[] uuidBytes(UUID u) {
        if (u == null) return null;
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(u.getMostSignificantBits());
        bb.putLong(u.getLeastSignificantBits());
        return bb.array();
    }

    private static void applySchema() throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            for (String ddl : DDL) st.execute(ddl);
        }
    }

    private static final String[] DDL = {
            // Minimal stand-ins for the shared economy tables the API reads/writes.
            """
            CREATE TABLE accounts (
              account_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
              account_type ENUM('PERSONAL','BUSINESS','GOVERNMENT','SYSTEM') NOT NULL,
              owner_uuid_bin BINARY(16) NULL,
              display_name VARCHAR(255) NULL,
              requires_authorization TINYINT(1) NOT NULL DEFAULT 0,
              is_archived TINYINT(1) NOT NULL DEFAULT 0,
              allow_overdraft TINYINT(1) NOT NULL DEFAULT 0,
              credit_limit DECIMAL(19,2) NOT NULL DEFAULT 0.00,
              PRIMARY KEY (account_id),
              KEY idx_acct_owner (owner_uuid_bin)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,
            """
            CREATE TABLE account_balances_mat (
              account_id INT UNSIGNED NOT NULL,
              balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
              version BIGINT NOT NULL DEFAULT 0,
              PRIMARY KEY (account_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,
            // Ledger tables for the write side (transfers). No balance-maintenance
            // trigger here (JDBC can't run DELIMITER blocks) — transfers don't read a
            // balance back after posting, and overdraft checks read the seeded balance.
            """
            CREATE TABLE ledger_txns (
              txn_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
              trade_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              settlement_time DATETIME NULL,
              message VARCHAR(255) NOT NULL,
              initiator_uuid_bin BINARY(16) NOT NULL,
              authorizer_uuid_bin BINARY(16) NULL,
              plugin_system VARCHAR(64) NULL,
              client_dedup_key BINARY(32) NULL,
              PRIMARY KEY (txn_id),
              UNIQUE KEY uq_ledger_dedup (client_dedup_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,
            """
            CREATE TABLE ledger_postings (
              posting_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
              txn_id BIGINT UNSIGNED NOT NULL,
              account_id INT UNSIGNED NOT NULL,
              amount DECIMAL(19,2) NOT NULL,
              memo VARCHAR(255) NULL,
              PRIMARY KEY (posting_id),
              KEY idx_postings_account (account_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,
            """
            CREATE TABLE firm (
              firm_id INT NOT NULL AUTO_INCREMENT,
              display_name VARCHAR(255) NOT NULL,
              proprietor_uuid_bin BINARY(16) NULL,
              discord_url VARCHAR(255) NULL,
              hq_region VARCHAR(64) NULL,
              default_account_id INT UNSIGNED NULL,
              is_archived TINYINT(1) NOT NULL DEFAULT 0,
              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (firm_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,
            """
            CREATE TABLE firm_accounts (
              firm_id INT NOT NULL,
              account_id INT UNSIGNED NOT NULL,
              added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              removed_at TIMESTAMP NULL,
              PRIMARY KEY (firm_id, account_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,
            """
            CREATE TABLE firm_players (
              player_uuid_bin BINARY(16) NOT NULL,
              current_name VARCHAR(64) NULL,
              name_lower VARCHAR(64) AS (LOWER(current_name)) VIRTUAL,
              first_seen TIMESTAMP NULL,
              PRIMARY KEY (player_uuid_bin)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,
            // ChestShop analytics tables read by the market endpoints — mirrors
            // economy-schema V6__chestshop_sales.sql (the production definition).
            """
            CREATE TABLE chestshop_sale (
              sale_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
              txn_id            BIGINT UNSIGNED NULL,
              occurred_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
              direction         ENUM('BUY','SELL') NOT NULL,
              customer_uuid_bin BINARY(16)      NOT NULL,
              shop_account_id     INT UNSIGNED  NULL,
              shop_account_type   ENUM('PERSONAL','BUSINESS','GOVERNMENT','SYSTEM') NULL,
              shop_firm_id        INT           NULL,
              shop_owner_uuid_bin BINARY(16)    NULL,
              is_admin_shop       TINYINT(1)    NOT NULL DEFAULT 0,
              material          VARCHAR(64)     NOT NULL,
              item_key          VARCHAR(190)    NOT NULL,
              item_name         VARCHAR(255)    NOT NULL,
              item_custom       TINYINT(1)      NOT NULL DEFAULT 0,
              item_data         MEDIUMTEXT      NULL,
              quantity          INT             NOT NULL,
              unit_price        DECIMAL(19,4)   NOT NULL,
              total_price       DECIMAL(19,2)   NOT NULL,
              tax_amount        DECIMAL(19,2)   NOT NULL DEFAULT 0.00,
              world             VARCHAR(64)     NULL,
              sign_x INT NULL, sign_y INT NULL, sign_z INT NULL,
              shop_stock        INT             NULL,
              PRIMARY KEY (sale_id),
              KEY idx_cs_time      (occurred_at),
              KEY idx_cs_item      (item_key, occurred_at),
              KEY idx_cs_firm      (shop_firm_id, occurred_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,
            """
            CREATE TABLE chestshop_shop (
              shop_id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
              world            VARCHAR(64)  NOT NULL,
              sign_x INT NOT NULL, sign_y INT NOT NULL, sign_z INT NOT NULL,
              is_admin_shop       TINYINT(1)   NOT NULL DEFAULT 0,
              shop_account_id     INT UNSIGNED NULL,
              shop_account_type   ENUM('PERSONAL','BUSINESS','GOVERNMENT','SYSTEM') NULL,
              shop_firm_id        INT          NULL,
              shop_owner_uuid_bin BINARY(16)   NULL,
              material         VARCHAR(64)  NOT NULL,
              item_key         VARCHAR(190) NOT NULL,
              item_name        VARCHAR(255) NOT NULL,
              item_custom      TINYINT(1)   NOT NULL DEFAULT 0,
              item_data        MEDIUMTEXT   NULL,
              buy_price        DECIMAL(19,2) NULL,
              sell_price       DECIMAL(19,2) NULL,
              batch_qty        INT          NOT NULL,
              current_stock    INT          NULL,
              active           TINYINT(1)   NOT NULL DEFAULT 1,
              created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
              stock_at         TIMESTAMP    NULL,
              last_seen        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (shop_id),
              UNIQUE KEY uq_shop_location (world, sign_x, sign_y, sign_z),
              KEY idx_shop_item   (item_key, active),
              KEY idx_shop_mat    (material, active),
              KEY idx_shop_firm   (shop_firm_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,
    };
}
