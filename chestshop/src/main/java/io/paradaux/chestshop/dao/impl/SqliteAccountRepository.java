package io.paradaux.chestshop.dao.impl;

import com.google.inject.Singleton;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.SelectArg;
import io.paradaux.chestshop.dao.AccountRepository;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.database.DaoCreator;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite-backed {@link AccountRepository}, wrapping the ORMlite {@code Account} DAO
 * ({@code users.db}). All ORMlite/SQLite specifics — the {@code QueryBuilder},
 * {@link SelectArg} binding, and the checked {@link SQLException} — are confined here,
 * behind the storage-agnostic interface; the rest of the plugin never sees a
 * {@code Dao}.
 */
@Singleton
public class SqliteAccountRepository implements AccountRepository {

    private final Dao<Account, String> dao;

    public SqliteAccountRepository() {
        try {
            this.dao = DaoCreator.getDaoAndCreateTable(Account.class);
        } catch (SQLException e) {
            // Fail fast on enable — the account store is required to resolve shop owners.
            throw new IllegalStateException("Unable to open the ChestShop account store", e);
        }
    }

    @Override
    public Optional<Account> findLatestByUuid(UUID uuid) {
        return query(() -> Optional.ofNullable(
                dao.queryBuilder().orderBy("lastSeen", false)
                        .where().eq("uuid", new SelectArg(uuid)).queryForFirst()));
    }

    @Override
    public Optional<Account> findLatestByName(String name) {
        return query(() -> Optional.ofNullable(
                dao.queryBuilder().orderBy("lastSeen", false)
                        .where().eq("name", new SelectArg(name)).queryForFirst()));
    }

    @Override
    public Optional<Account> findByShortName(String shortName) {
        return query(() -> Optional.ofNullable(
                dao.queryBuilder().where().eq("shortName", new SelectArg(shortName)).queryForFirst()));
    }

    @Override
    public Optional<Account> findByUuidAndName(UUID uuid, String name) {
        return query(() -> Optional.ofNullable(
                dao.queryBuilder().where()
                        .eq("uuid", new SelectArg(uuid)).and().eq("name", new SelectArg(name))
                        .queryForFirst()));
    }

    @Override
    public void save(Account account) {
        query(() -> {
            dao.createOrUpdate(account);
            return null;
        });
    }

    @Override
    public long count() {
        return query(() -> dao.queryBuilder().countOf());
    }

    /** Funnels the checked {@link SQLException} the ORMlite DAO throws into one place. */
    private <T> T query(SqlCall<T> call) {
        try {
            return call.run();
        } catch (SQLException e) {
            throw new IllegalStateException("ChestShop account store query failed", e);
        }
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T run() throws SQLException;
    }
}
