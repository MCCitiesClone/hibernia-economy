package io.paradaux.chestshop.dao;

import io.paradaux.chestshop.database.Account;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for the account store: the username ↔ UUID ↔ shortened-name
 * mapping (the {@code accounts} table in {@code users.db}) a shop sign resolves an
 * owner against. Pure storage — caching, shortened-name allocation, admin/server
 * account handling and access rules all live in
 * {@link io.paradaux.chestshop.services.AccountService}.
 *
 * <p>Storage-agnostic on purpose: the current implementation is SQLite
 * ({@link io.paradaux.chestshop.dao.impl.SqliteAccountRepository}); swapping to the
 * shared MariaDB later is a new implementation behind this same contract, with no
 * caller changes. Lookups return {@link Optional}; a backing-store failure surfaces
 * as an unchecked {@link IllegalStateException}, never a leaked {@code SQLException}.
 */
public interface AccountRepository {

    /** The most-recently-seen account for {@code uuid} (a player may have several name rows). */
    Optional<Account> findLatestByUuid(UUID uuid);

    /** The most-recently-seen account with the exact (non-shortened) {@code name}. */
    Optional<Account> findLatestByName(String name);

    /** The account whose shortened sign name equals {@code shortName}. */
    Optional<Account> findByShortName(String shortName);

    /** The account matching both {@code uuid} and {@code name} (a specific name row). */
    Optional<Account> findByUuidAndName(UUID uuid, String name);

    /** Inserts or updates {@code account} (keyed by its shortened name). */
    void save(Account account);

    /** Total stored account rows (including the admin account). */
    long count();
}
