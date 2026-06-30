package io.paradaux.chestshop.guice;

import com.google.inject.AbstractModule;
import io.paradaux.chestshop.database.ChestShopDatabases;
import io.paradaux.chestshop.database.SqliteMyBatis;
import io.paradaux.chestshop.mappers.AccountMapper;
import io.paradaux.chestshop.mappers.ItemCodeMapper;

import java.io.File;

/**
 * Wires ChestShop's persistence: one MyBatis {@link SqliteMyBatis} store per SQLite file
 * ({@code users.db} for accounts, {@code items.db} for item codes), binding each store's
 * auto-committing mapper proxy so services inject {@link AccountMapper}/{@link ItemCodeMapper}
 * directly — the same service→mapper shape the other plugins use, over SQLite rather than
 * the shared MariaDB (PAR-282). Replaces the ORMlite {@code DaoCreator} + the
 * version-stepped {@code Migrations}: tables are created idempotently here, with a
 * best-effort {@code ALTER} to top up a pre-existing older accounts schema.
 */
public class DatabaseModule extends AbstractModule {

    private static final String[] ACCOUNTS_REQUIRED = {
            """
            CREATE TABLE IF NOT EXISTS accounts (
                name           VARCHAR NOT NULL,
                shortName      VARCHAR NOT NULL PRIMARY KEY,
                uuid           VARCHAR NOT NULL,
                lastSeen       BIGINT  NOT NULL DEFAULT 0,
                ignoreMessages BOOLEAN NOT NULL DEFAULT 0
            )
            """
    };
    private static final String[] ACCOUNTS_OPTIONAL = {
            // Top up a pre-existing accounts table created before these columns existed
            // (was the ORMlite v2/v5 ALTERs). Ignored when the column is already present.
            "ALTER TABLE accounts ADD COLUMN lastSeen BIGINT NOT NULL DEFAULT 0",
            "ALTER TABLE accounts ADD COLUMN ignoreMessages BOOLEAN NOT NULL DEFAULT 0",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_name_uuid ON accounts(name, uuid)"
    };

    private static final String[] ITEMS_REQUIRED = {
            """
            CREATE TABLE IF NOT EXISTS items (
                id   INTEGER PRIMARY KEY AUTOINCREMENT,
                code VARCHAR NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_items_code ON items(code)"
    };
    private static final String[] NONE = {};

    private final File usersDatabase;
    private final File itemsDatabase;

    public DatabaseModule(File usersDatabase, File itemsDatabase) {
        this.usersDatabase = usersDatabase;
        this.itemsDatabase = itemsDatabase;
    }

    @Override
    protected void configure() {
        SqliteMyBatis users = SqliteMyBatis.open(usersDatabase, AccountMapper.class, ACCOUNTS_REQUIRED, ACCOUNTS_OPTIONAL);
        SqliteMyBatis items = SqliteMyBatis.open(itemsDatabase, ItemCodeMapper.class, ITEMS_REQUIRED, NONE);

        bind(AccountMapper.class).toInstance(users.getMapper(AccountMapper.class));
        bind(ItemCodeMapper.class).toInstance(items.getMapper(ItemCodeMapper.class));
        bind(ChestShopDatabases.class).toInstance(new ChestShopDatabases(users, items));
    }
}
