package io.paradaux.treasury.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * The unified player directory ({@code economy_players}): UUID ↔ current name and
 * last-login epoch. Treasury owns the writer; reads back a deterministic
 * name → UUID mapping that doesn't depend on Bukkit usercache state (PAR-35,
 * the foundation for PAR-150/PAR-149). UUIDs marshal to {@code BINARY(16)} via
 * the global {@code UuidBinaryTypeHandler}.
 */
@Mapper
public interface EconomyPlayerMapper {

    /** Upserts the player's current name + last-login epoch, bumping {@code last_seen}. */
    @Insert("""
            INSERT INTO economy_players (player_uuid_bin, current_name, last_login_epoch)
            VALUES (#{playerUuid}, #{currentName}, #{epochSeconds})
            ON DUPLICATE KEY UPDATE current_name     = VALUES(current_name),
                                    last_login_epoch = VALUES(last_login_epoch),
                                    last_seen        = CURRENT_TIMESTAMP
            """)
    void upsertLogin(@Param("playerUuid") UUID playerUuid,
                     @Param("currentName") String currentName,
                     @Param("epochSeconds") long epochSeconds);

    /** The player's last-login epoch (seconds), or {@code null} if never recorded. */
    @Select("SELECT last_login_epoch FROM economy_players WHERE player_uuid_bin = #{playerUuid}")
    Long findLastLogin(@Param("playerUuid") UUID playerUuid);

    /** Resolves a current name (case-insensitive) to a UUID, or {@code null} if unknown. */
    @Select("SELECT player_uuid_bin FROM economy_players WHERE name_lower = LOWER(#{name})")
    UUID findUuidByName(@Param("name") String name);

    /** The player's last-known name, or {@code null} if the player isn't in the directory. */
    @Select("SELECT current_name FROM economy_players WHERE player_uuid_bin = #{playerUuid}")
    String findNameByUuid(@Param("playerUuid") UUID playerUuid);
}
