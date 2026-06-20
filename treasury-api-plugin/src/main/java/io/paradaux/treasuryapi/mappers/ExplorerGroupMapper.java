package io.paradaux.treasuryapi.mappers;

import io.paradaux.treasuryapi.model.SyncedGroup;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * Reads/writes explorer group membership for the reconciliation cron. The cron
 * only ever touches its own {@code source = 'luckperms'} rows, so manual grants
 * made in the explorer admin tool are never disturbed. UUID params use the
 * registered {@code UuidBinaryTypeHandler}.
 */
@Mapper
public interface ExplorerGroupMapper {

    @Select("SELECT group_id AS groupId, luckperms_node AS luckpermsNode "
            + "FROM explorer_group WHERE luckperms_node IS NOT NULL")
    List<SyncedGroup> listSyncedGroups();

    @Select("SELECT player_uuid_bin FROM explorer_group_member "
            + "WHERE group_id = #{groupId} AND source = 'luckperms'")
    List<UUID> listLuckpermsMemberUuids(@Param("groupId") int groupId);

    @Insert("INSERT IGNORE INTO explorer_group_member (group_id, player_uuid_bin, source) "
            + "VALUES (#{groupId}, #{uuid}, 'luckperms')")
    void addLuckpermsMember(@Param("groupId") int groupId, @Param("uuid") UUID uuid);

    @Delete("DELETE FROM explorer_group_member "
            + "WHERE group_id = #{groupId} AND player_uuid_bin = #{uuid} AND source = 'luckperms'")
    void removeLuckpermsMember(@Param("groupId") int groupId, @Param("uuid") UUID uuid);
}
