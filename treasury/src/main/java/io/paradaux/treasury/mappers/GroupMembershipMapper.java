package io.paradaux.treasury.mappers;

import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * LuckPerms-group access on the consolidated {@code account_group_access} table
 * (PAR-249). Same ordered scale and soft-delete semantics as
 * {@link MembershipMapper}, keyed by {@code lp_group} instead of a UUID.
 */
@Mapper
public interface GroupMembershipMapper {

    // ---- Checks (any of the player's groups holds the level) ----

    @Select("<script>" +
            "SELECT COUNT(*) FROM account_group_access " +
            "WHERE account_id = #{accountId} AND removed_at IS NULL " +
            "AND level IN ('MEMBER','AUTHORIZER') AND lp_group IN " +
            "<foreach item='item' collection='groups' open='(' separator=',' close=')'>#{item}</foreach>" +
            "</script>")
    int isAnyGroupMember(@Param("accountId") int accountId,
                         @Param("groups") Collection<String> groups);

    @Select("<script>" +
            "SELECT COUNT(*) FROM account_group_access " +
            "WHERE account_id = #{accountId} AND removed_at IS NULL " +
            "AND level = 'AUTHORIZER' AND lp_group IN " +
            "<foreach item='item' collection='groups' open='(' separator=',' close=')'>#{item}</foreach>" +
            "</script>")
    int isAnyGroupAuthorizer(@Param("accountId") int accountId,
                             @Param("groups") Collection<String> groups);

    @Select("<script>" +
            "SELECT COUNT(*) FROM account_group_access " +
            "WHERE account_id = #{accountId} AND removed_at IS NULL " +
            "AND level = 'VIEWER' AND lp_group IN " +
            "<foreach item='item' collection='groups' open='(' separator=',' close=')'>#{item}</foreach>" +
            "</script>")
    int isAnyGroupViewer(@Param("accountId") int accountId,
                         @Param("groups") Collection<String> groups);

    // ---- Grants ----

    @Insert("INSERT INTO account_group_access(account_id, lp_group, level, added_by_uuid_bin) " +
            "VALUES(#{accountId}, #{lpGroup}, 'MEMBER', #{addedByUuid}) " +
            "ON DUPLICATE KEY UPDATE " +
            "level = CASE WHEN removed_at IS NULL AND level = 'AUTHORIZER' THEN 'AUTHORIZER' ELSE 'MEMBER' END, " +
            "removed_at = NULL, added_by_uuid_bin = VALUES(added_by_uuid_bin)")
    int addGroupMember(@Param("accountId") int accountId,
                       @Param("lpGroup") String lpGroup,
                       @Param("addedByUuid") UUID addedByUuid);

    @Insert("INSERT INTO account_group_access(account_id, lp_group, level, added_by_uuid_bin) " +
            "VALUES(#{accountId}, #{lpGroup}, 'VIEWER', #{addedByUuid}) " +
            "ON DUPLICATE KEY UPDATE " +
            "level = CASE WHEN removed_at IS NULL AND level IN ('MEMBER','AUTHORIZER') THEN level ELSE 'VIEWER' END, " +
            "removed_at = NULL, added_by_uuid_bin = VALUES(added_by_uuid_bin)")
    int addGroupViewer(@Param("accountId") int accountId,
                       @Param("lpGroup") String lpGroup,
                       @Param("addedByUuid") UUID addedByUuid);

    @Update("UPDATE account_group_access SET level = 'AUTHORIZER', removed_at = NULL " +
            "WHERE account_id = #{accountId} AND lp_group = #{lpGroup}")
    int promoteGroupToAuthorizer(@Param("accountId") int accountId,
                                 @Param("lpGroup") String lpGroup);

    // ---- Removals (soft-delete) ----

    @Update("UPDATE account_group_access SET removed_at = CURRENT_TIMESTAMP(3) " +
            "WHERE account_id = #{accountId} AND lp_group = #{lpGroup} " +
            "AND level IN ('MEMBER','AUTHORIZER') AND removed_at IS NULL")
    int removeGroupMember(@Param("accountId") int accountId,
                          @Param("lpGroup") String lpGroup);

    @Update("UPDATE account_group_access SET level = 'MEMBER' " +
            "WHERE account_id = #{accountId} AND lp_group = #{lpGroup} " +
            "AND level = 'AUTHORIZER' AND removed_at IS NULL")
    int removeGroupAuthorizer(@Param("accountId") int accountId,
                              @Param("lpGroup") String lpGroup);

    @Update("UPDATE account_group_access SET removed_at = CURRENT_TIMESTAMP(3) " +
            "WHERE account_id = #{accountId} AND lp_group = #{lpGroup} " +
            "AND level = 'VIEWER' AND removed_at IS NULL")
    int removeGroupViewer(@Param("accountId") int accountId,
                          @Param("lpGroup") String lpGroup);

    // ---- Listing ----

    @Select("SELECT lp_group FROM account_group_access " +
            "WHERE account_id = #{accountId} AND level IN ('MEMBER','AUTHORIZER') AND removed_at IS NULL " +
            "ORDER BY created_at")
    List<String> getGroupMembers(@Param("accountId") int accountId);

    @Select("SELECT lp_group FROM account_group_access " +
            "WHERE account_id = #{accountId} AND level = 'AUTHORIZER' AND removed_at IS NULL " +
            "ORDER BY created_at")
    List<String> getGroupAuthorizers(@Param("accountId") int accountId);

    @Select("SELECT lp_group FROM account_group_access " +
            "WHERE account_id = #{accountId} AND level = 'VIEWER' AND removed_at IS NULL " +
            "ORDER BY created_at")
    List<String> getGroupViewers(@Param("accountId") int accountId);
}
