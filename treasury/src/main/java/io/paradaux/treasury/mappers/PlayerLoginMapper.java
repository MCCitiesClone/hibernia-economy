package io.paradaux.treasury.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

@Mapper
public interface PlayerLoginMapper {

    /**
     * Returns the Unix epoch second of this player's last recorded login,
     * or {@code null} if they have never logged in before (first ever join).
     */
    @Select("SELECT last_login_epoch FROM player_login_times WHERE player_uuid_bin = #{playerUuid}")
    Long findLastLogin(@Param("playerUuid") UUID playerUuid);

    /**
     * Inserts or updates the recorded login time for a player.
     * The upsert is intentionally unconditional — callers should record the new
     * time AFTER reading the previous one so the two reads/writes form a consistent pair.
     */
    @Insert("""
            INSERT INTO player_login_times (player_uuid_bin, last_login_epoch)
            VALUES (#{playerUuid}, #{epochSeconds})
            ON DUPLICATE KEY UPDATE last_login_epoch = #{epochSeconds}
            """)
    void upsertLogin(@Param("playerUuid") UUID playerUuid, @Param("epochSeconds") long epochSeconds);
}
