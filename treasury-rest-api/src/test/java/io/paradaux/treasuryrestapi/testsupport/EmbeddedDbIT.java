package io.paradaux.treasuryrestapi.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.UUID;

/**
 * Base for integration tests that need a real database: a full Spring context
 * pointed at the shared {@link EmbeddedMariaDb}, a {@link MockMvc}, and seed
 * builders for accounts / firms / players. Tables are truncated before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
public abstract class EmbeddedDbIT {

    @Autowired protected MockMvc mvc;
    @Autowired protected DataSource dataSource;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        EmbeddedMariaDb.ensureStarted();
        registry.add("spring.datasource.url", EmbeddedMariaDb::jdbcUrl);
        registry.add("spring.datasource.username", EmbeddedMariaDb::username);
        registry.add("spring.datasource.password", EmbeddedMariaDb::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
    }

    @BeforeEach
    void truncate() {
        EmbeddedMariaDb.truncateAll();
    }

    // ── seed: economy stand-ins ─────────────────────────────────────────────────

    protected void insertAccount(int accountId, String type, UUID ownerUuid, String displayName) {
        exec("INSERT INTO accounts (account_id, account_type, owner_uuid_bin, display_name) VALUES (?,?,?,?)",
                ps -> {
                    ps.setInt(1, accountId);
                    ps.setString(2, type);
                    setUuid(ps, 3, ownerUuid);
                    ps.setString(4, displayName);
                });
    }

    protected void insertFirm(int firmId, String displayName) {
        exec("INSERT INTO firm (firm_id, display_name) VALUES (?,?)", ps -> {
            ps.setInt(1, firmId);
            ps.setString(2, displayName);
        });
    }

    /** Seed a firm with a proprietor (and optional default account) for admin firm tests. */
    protected void insertFirm(int firmId, String displayName, UUID proprietorUuid, Integer defaultAccountId) {
        exec("INSERT INTO firm (firm_id, display_name, proprietor_uuid_bin, default_account_id) VALUES (?,?,?,?)",
                ps -> {
                    ps.setInt(1, firmId);
                    ps.setString(2, displayName);
                    setUuid(ps, 3, proprietorUuid);
                    if (defaultAccountId == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, defaultAccountId);
                });
    }

    /** Link an account to a firm (live link — removed_at NULL). */
    protected void linkFirmAccount(int firmId, int accountId) {
        exec("INSERT INTO firm_accounts (firm_id, account_id) VALUES (?,?)", ps -> {
            ps.setInt(1, firmId);
            ps.setInt(2, accountId);
        });
    }

    /** Mark a firm archived up-front (for idempotency tests). */
    protected void archiveFirm(int firmId) {
        exec("UPDATE firm SET is_archived = 1 WHERE firm_id = ?", ps -> ps.setInt(1, firmId));
    }

    protected boolean isFirmArchived(int firmId) {
        return queryInt("SELECT is_archived FROM firm WHERE firm_id = ?", firmId) == 1;
    }

    protected boolean isAccountArchived(int accountId) {
        return queryInt("SELECT is_archived FROM accounts WHERE account_id = ?", accountId) == 1;
    }

    /** Count of live (removed_at IS NULL) firm↔account links for a firm. */
    protected long liveFirmAccountLinks(int firmId) {
        return queryInt("SELECT COUNT(*) FROM firm_accounts WHERE firm_id = ? AND removed_at IS NULL", firmId);
    }

    private int queryInt(String sql, int arg) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void insertPlayer(UUID uuid, String currentName) {
        exec("INSERT INTO firm_players (player_uuid_bin, current_name) VALUES (?,?)", ps -> {
            ps.setBytes(1, EmbeddedMariaDb.uuidBytes(uuid));
            ps.setString(2, currentName);
        });
    }

    /** Seed a materialised balance row (transfers lock account_balances_mat FOR UPDATE). */
    protected void seedBalance(int accountId, String balance) {
        exec("INSERT INTO account_balances_mat (account_id, balance, version) VALUES (?,?,0)", ps -> {
            ps.setInt(1, accountId);
            ps.setBigDecimal(2, new java.math.BigDecimal(balance));
        });
    }

    // ── seed: ChestShop analytics ───────────────────────────────────────────────

    /**
     * Insert one {@code chestshop_sale} row. World/sign default to {@code world}
     * at the origin; tax 0; unit price derived from total/quantity.
     */
    protected void insertSale(String direction, UUID customer, String accountType, Integer firmId,
                              UUID ownerUuid, boolean adminShop, String material, String itemKey,
                              String itemName, int quantity, String totalPrice,
                              java.time.LocalDateTime occurredAt) {
        java.math.BigDecimal total = new java.math.BigDecimal(totalPrice);
        java.math.BigDecimal unit = quantity > 0
                ? total.divide(java.math.BigDecimal.valueOf(quantity), 4, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;
        exec("INSERT INTO chestshop_sale (occurred_at, direction, customer_uuid_bin, shop_account_type, "
                + "shop_firm_id, shop_owner_uuid_bin, is_admin_shop, material, item_key, item_name, "
                + "quantity, unit_price, total_price, world, sign_x, sign_y, sign_z) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, 'world', 0, 0, 0)", ps -> {
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(occurredAt));
            ps.setString(2, direction);
            ps.setBytes(3, EmbeddedMariaDb.uuidBytes(customer));
            ps.setString(4, accountType);
            if (firmId == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, firmId);
            if (ownerUuid == null) ps.setNull(6, Types.BINARY); else ps.setBytes(6, EmbeddedMariaDb.uuidBytes(ownerUuid));
            ps.setInt(7, adminShop ? 1 : 0);
            ps.setString(8, material);
            ps.setString(9, itemKey);
            ps.setString(10, itemName);
            ps.setInt(11, quantity);
            ps.setBigDecimal(12, unit);
            ps.setBigDecimal(13, total);
        });
    }

    /** Insert one {@code chestshop_shop} row. */
    protected void insertShop(String world, int x, int y, int z, boolean adminShop, String accountType,
                              Integer firmId, UUID ownerUuid, String material, String itemKey,
                              String itemName, String buyPrice, String sellPrice, int batchQty,
                              Integer currentStock, boolean active) {
        exec("INSERT INTO chestshop_shop (world, sign_x, sign_y, sign_z, is_admin_shop, shop_account_type, "
                + "shop_firm_id, shop_owner_uuid_bin, material, item_key, item_name, buy_price, sell_price, "
                + "batch_qty, current_stock, active) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", ps -> {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.setInt(5, adminShop ? 1 : 0);
            ps.setString(6, accountType);
            if (firmId == null) ps.setNull(7, Types.INTEGER); else ps.setInt(7, firmId);
            if (ownerUuid == null) ps.setNull(8, Types.BINARY); else ps.setBytes(8, EmbeddedMariaDb.uuidBytes(ownerUuid));
            ps.setString(9, material);
            ps.setString(10, itemKey);
            ps.setString(11, itemName);
            if (buyPrice == null) ps.setNull(12, Types.DECIMAL); else ps.setBigDecimal(12, new java.math.BigDecimal(buyPrice));
            if (sellPrice == null) ps.setNull(13, Types.DECIMAL); else ps.setBigDecimal(13, new java.math.BigDecimal(sellPrice));
            ps.setInt(14, batchQty);
            if (currentStock == null) ps.setNull(15, Types.INTEGER); else ps.setInt(15, currentStock);
            ps.setInt(16, active ? 1 : 0);
        });
    }

    // ── tiny read helpers for assertions ────────────────────────────────────────

    protected long rowCount(String table) {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected java.math.BigDecimal sumPostings(int accountId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM ledger_postings WHERE account_id = ?")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── tiny JDBC plumbing ──────────────────────────────────────────────────────

    @FunctionalInterface protected interface Binder { void bind(PreparedStatement ps) throws Exception; }

    protected void exec(String sql, Binder binder) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected long execReturningKey(String sql, Binder binder) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(ps);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setUuid(PreparedStatement ps, int i, UUID u) throws Exception {
        if (u == null) ps.setNull(i, Types.BINARY); else ps.setBytes(i, EmbeddedMariaDb.uuidBytes(u));
    }
}
