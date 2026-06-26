package io.paradaux.chestshop.dao.impl;

import com.google.inject.Singleton;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.SelectArg;
import io.paradaux.chestshop.dao.ItemCodeRepository;
import io.paradaux.chestshop.database.DaoCreator;
import io.paradaux.chestshop.database.Item;

import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * SQLite-backed {@link ItemCodeRepository}, wrapping the ORMlite {@code Item} DAO
 * ({@code items.db}). All ORMlite/SQLite specifics are confined here, behind the
 * storage-agnostic interface — the rest of the plugin never sees a {@code Dao} or
 * a {@code SQLException}.
 */
@Singleton
public class SqliteItemCodeRepository implements ItemCodeRepository {

    private final Dao<Item, Integer> dao;

    public SqliteItemCodeRepository() {
        try {
            this.dao = DaoCreator.getDaoAndCreateTable(Item.class);
        } catch (SQLException e) {
            // Fail fast on enable — the item store is required for shops with codes.
            throw new IllegalStateException("Unable to open the ChestShop item-code store", e);
        }
    }

    @Override
    public Optional<Integer> findIdByBlob(String blob) {
        return query(() -> {
            Item item = dao.queryBuilder().where().eq("code", new SelectArg(blob)).queryForFirst();
            return Optional.ofNullable(item).map(Item::getId);
        });
    }

    @Override
    public int insert(String blob) {
        return query(() -> {
            Item item = new Item(blob);
            dao.create(item);
            return item.getId();
        });
    }

    @Override
    public Optional<String> findBlobById(int id) {
        return query(() -> {
            Item item = dao.queryForId(id);
            return Optional.ofNullable(item).map(Item::getBase64ItemCode);
        });
    }

    @Override
    public void updateBlob(int id, String blob) {
        query(() -> {
            Item item = dao.queryForId(id);
            if (item != null) {
                item.setBase64ItemCode(blob);
                dao.update(item);
            }
            return null;
        });
    }

    @Override
    public void forEach(Consumer<StoredItem> action) {
        try (CloseableIterator<Item> it = dao.iterator()) {
            while (it.hasNext()) {
                Item item = it.next();
                action.accept(new StoredItem(item.getId(), item.getBase64ItemCode()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed iterating the item-code store", e);
        }
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
            throw new IllegalStateException("ChestShop item-code store query failed", e);
        }
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T run() throws SQLException;
    }
}
