package io.paradaux.chestshop.mappers;

import io.paradaux.chestshop.model.Account;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * MyBatis mapper for the {@code accounts} table ({@code users.db}) — the username ↔ UUID
 * ↔ shortened-name store a shop sign resolves an owner against. Annotation SQL in the
 * SQLite dialect; {@code uuid} is text and {@code lastSeen} epoch-millis, mapped by the
 * registered {@code UuidStringTypeHandler} / {@code DateLongTypeHandler}. The same
 * mapper shape the other plugins use, but over SQLite rather than the shared MariaDB
 * (PAR-282).
 */
@Mapper
public interface AccountMapper {

    String COLUMNS = "name, shortName, uuid, lastSeen, ignoreMessages";

    /** The most-recently-seen account for {@code uuid} (a player may have several name rows). */
    @Select("SELECT " + COLUMNS + " FROM accounts WHERE uuid = #{uuid} ORDER BY lastSeen DESC LIMIT 1")
    Account findLatestByUuid(@Param("uuid") UUID uuid);

    /** The most-recently-seen account with the exact (non-shortened) {@code name}. */
    @Select("SELECT " + COLUMNS + " FROM accounts WHERE name = #{name} ORDER BY lastSeen DESC LIMIT 1")
    Account findLatestByName(@Param("name") String name);

    /** The account whose shortened sign name equals {@code shortName}. */
    @Select("SELECT " + COLUMNS + " FROM accounts WHERE shortName = #{shortName} LIMIT 1")
    Account findByShortName(@Param("shortName") String shortName);

    /** The account matching both {@code uuid} and {@code name} (a specific name row). */
    @Select("SELECT " + COLUMNS + " FROM accounts WHERE uuid = #{uuid} AND name = #{name} LIMIT 1")
    Account findByUuidAndName(@Param("uuid") UUID uuid, @Param("name") String name);

    /** Total stored account rows (including the admin account). */
    @Select("SELECT COUNT(*) FROM accounts")
    long count();

    /** Inserts or updates {@code account}, keyed by its shortened name (the primary key). */
    @Insert("""
            INSERT INTO accounts(name, shortName, uuid, lastSeen, ignoreMessages)
            VALUES(#{name}, #{shortName}, #{uuid}, #{lastSeen}, #{ignoreMessages})
            ON CONFLICT(shortName) DO UPDATE SET
                name = excluded.name,
                uuid = excluded.uuid,
                lastSeen = excluded.lastSeen,
                ignoreMessages = excluded.ignoreMessages
            """)
    void save(Account account);
}
