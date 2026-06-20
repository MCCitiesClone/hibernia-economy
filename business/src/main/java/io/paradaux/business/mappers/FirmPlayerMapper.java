package io.paradaux.business.mappers;

import io.paradaux.business.model.FirmPlayer;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface FirmPlayerMapper {

    @Insert("""
        INSERT INTO firm_players (player_uuid_bin, current_name)
        VALUES (uuid_to_bin(#{playerUuid}), #{currentName})
        ON DUPLICATE KEY UPDATE
          current_name = VALUES(current_name),
          last_seen = CURRENT_TIMESTAMP
        """)
    int upsert(@Param("playerUuid") String playerUuid,
               @Param("currentName") String currentName);

    @Select("""
        SELECT bin_to_uuid(player_uuid_bin) AS playerUuid,
               current_name                 AS currentName,
               name_lower                   AS nameLower,
               first_seen                   AS firstSeen,
               last_seen                    AS lastSeen
        FROM firm_players
        WHERE player_uuid_bin = uuid_to_bin(#{playerUuid})
        """)
    FirmPlayer getByUuid(@Param("playerUuid") String playerUuid);

    @Select("""
        SELECT bin_to_uuid(player_uuid_bin) AS playerUuid,
               current_name                 AS currentName,
               name_lower                   AS nameLower,
               first_seen                   AS firstSeen,
               last_seen                    AS lastSeen
        FROM firm_players
        WHERE name_lower = LOWER(#{name})
        """)
    FirmPlayer getByName(@Param("name") String name);

    @Select("""
        SELECT bin_to_uuid(player_uuid_bin) AS playerUuid,
               current_name                 AS currentName,
               name_lower                   AS nameLower,
               first_seen                   AS firstSeen,
               last_seen                    AS lastSeen
        FROM firm_players
        WHERE name_lower LIKE CONCAT(LOWER(#{prefix}), '%')
        ORDER BY last_seen DESC
        LIMIT #{limit}
        """)
    List<FirmPlayer> searchByPrefix(@Param("prefix") String prefix,
                                    @Param("limit") int limit);

    @Select("""
        SELECT COUNT(*)
        FROM firm_players
        WHERE player_uuid_bin = uuid_to_bin(#{playerUuid})
        """)
    int existsByUuid(@Param("playerUuid") String playerUuid);

    @Update("""
        UPDATE firm_players
        SET current_name = #{currentName}
        WHERE player_uuid_bin = uuid_to_bin(#{playerUuid})
        """)
    int updateName(@Param("playerUuid") String playerUuid,
                   @Param("currentName") String currentName);

}
