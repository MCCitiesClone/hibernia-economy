package io.paradaux.chestshop.database;

import io.paradaux.chestshop.ChestShop;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.dao.LruObjectCache;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.jdbc.db.SqliteDatabaseType;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.security.InvalidParameterException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Creates a DAO appropriate for the plugin
 *
 * @author Andrzej Pomirski
 */
public class DaoCreator {

    /**
     * One {@link ConnectionSource} per database file (keyed by JDBC URI),
     * reused across all DAOs for that file and closed on plugin disable. Each
     * {@code getDao} call used to open a fresh {@link JdbcConnectionSource} that
     * was never closed, leaking a connection source per call (ADT-42).
     */
    private static final Map<String, ConnectionSource> CONNECTION_SOURCES = new HashMap<>();

    /**
     * Returns a DAO for the given entity and with the given ID
     * @param entity Entity's class
     * @param <ENTITY> Type of the entity
     * @return Dao
     * @throws InvalidParameterException
     * @throws SQLException
     */
    public static <ENTITY, ID> Dao<ENTITY, ID> getDao(Class<ENTITY> entity) throws InvalidParameterException, SQLException {
        if (!entity.isAnnotationPresent(DatabaseFileName.class)) {
            throw new InvalidParameterException("Entity not annotated with @DatabaseFileName!");
        }

        String fileName = entity.getAnnotation(DatabaseFileName.class).value();
        String uri = ConnectionManager.getURI(ChestShop.loadFile(fileName));

        ConnectionSource connectionSource = getConnectionSource(uri);

        Dao<ENTITY, ID> dao = DaoManager.createDao(connectionSource, entity);
        dao.setObjectCache(new LruObjectCache(200));

        return dao;
    }

    private static synchronized ConnectionSource getConnectionSource(String uri) throws SQLException {
        ConnectionSource existing = CONNECTION_SOURCES.get(uri);
        if (existing != null) {
            return existing;
        }

        ConnectionSource created = new JdbcConnectionSource(uri, new SqliteDatabaseType());
        CONNECTION_SOURCES.put(uri, created);
        return created;
    }

    /**
     * Close every cached {@link ConnectionSource}. Call from the plugin's
     * {@code onDisable} so the SQLite handles are released on reload/shutdown.
     */
    public static synchronized void closeAll() {
        for (ConnectionSource source : CONNECTION_SOURCES.values()) {
            try {
                source.close();
            } catch (Exception e) {
                ChestShop.getBukkitLogger().log(Level.WARNING,
                        "Failed to close a ChestShop database connection source", e);
            }
        }
        CONNECTION_SOURCES.clear();
    }

    /**
     * Creates a dao as well as a default table, if doesn't exist
     * @see #getDao(Class)
     * @throws SQLException
     * @throws InvalidParameterException
     */
    public static <ENTITY, ID> Dao<ENTITY, ID> getDaoAndCreateTable(Class<ENTITY> entity) throws SQLException, InvalidParameterException {
        Dao<ENTITY, ID> dao = getDao(entity);

        TableUtils.createTableIfNotExists(dao.getConnectionSource(), entity);

        return dao;
    }
}
