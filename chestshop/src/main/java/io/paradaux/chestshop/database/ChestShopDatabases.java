package io.paradaux.chestshop.database;

import java.util.List;

/**
 * Holds the open SQLite-backed MyBatis stores so the plugin can close their connection
 * pools on {@code onDisable} (replacing the old static {@code DaoCreator.closeAll()}).
 */
public final class ChestShopDatabases {

    private final List<SqliteMyBatis> stores;

    public ChestShopDatabases(SqliteMyBatis... stores) {
        this.stores = List.of(stores);
    }

    /** Close every store's connection pool. */
    public void close() {
        stores.forEach(SqliteMyBatis::close);
    }
}
