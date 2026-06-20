package io.paradaux.treasury.mappers;

import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * LuckPerms-group-based membership and authorisation. Same soft-delete
 * semantics as {@link MembershipMapper}: removals stamp {@code left_at} /
 * {@code revoked_at}, reads filter on {@code … IS NULL}, re-adds use
 * {@code ON DUPLICATE KEY UPDATE} to clear the timestamp.
 */
@Mapper
public interface GroupMembershipMapper {

    // ---- Group Members ----

    @Insert("INSERT INTO account_group_members(account_id, lp_group, added_by_uuid_bin) " +
            "VALUES(#{accountId}, #{lpGroup}, #{addedByUuid}) " +
            "ON DUPLICATE KEY UPDATE left_at = NULL, added_by_uuid_bin = VALUES(added_by_uuid_bin)")
    int addGroupMember(@Param("accountId") int accountId,
                       @Param("lpGroup") String lpGroup,
                       @Param("addedByUuid") UUID addedByUuid);

    @Update("UPDATE account_group_members SET left_at = CURRENT_TIMESTAMP(3) " +
            "WHERE account_id = #{accountId} AND lp_group = #{lpGroup} AND left_at IS NULL")
    int removeGroupMember(@Param("accountId") int accountId,
                          @Param("lpGroup") String lpGroup);

    @Select("SELECT lp_group FROM account_group_members " +
            "WHERE account_id = #{accountId} AND left_at IS NULL ORDER BY created_at")
    List<String> getGroupMembers(@Param("accountId") int accountId);

    @Select("<script>" +
            "SELECT COUNT(*) FROM account_group_members " +
            "WHERE account_id = #{accountId} AND left_at IS NULL AND lp_group IN " +
            "<foreach item='item' collection='groups' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    int isAnyGroupMember(@Param("accountId") int accountId,
                         @Param("groups") Collection<String> groups);

    // ---- Group Authorizers ----

    @Insert("INSERT INTO account_group_authorizers(account_id, lp_group, added_by_uuid_bin) " +
            "VALUES(#{accountId}, #{lpGroup}, #{addedByUuid}) " +
            "ON DUPLICATE KEY UPDATE revoked_at = NULL, added_by_uuid_bin = VALUES(added_by_uuid_bin)")
    int addGroupAuthorizer(@Param("accountId") int accountId,
                           @Param("lpGroup") String lpGroup,
                           @Param("addedByUuid") UUID addedByUuid);

    @Update("UPDATE account_group_authorizers SET revoked_at = CURRENT_TIMESTAMP(3) " +
            "WHERE account_id = #{accountId} AND lp_group = #{lpGroup} AND revoked_at IS NULL")
    int removeGroupAuthorizer(@Param("accountId") int accountId,
                              @Param("lpGroup") String lpGroup);

    @Select("SELECT lp_group FROM account_group_authorizers " +
            "WHERE account_id = #{accountId} AND revoked_at IS NULL ORDER BY created_at")
    List<String> getGroupAuthorizers(@Param("accountId") int accountId);

    @Select("<script>" +
            "SELECT COUNT(*) FROM account_group_authorizers " +
            "WHERE account_id = #{accountId} AND revoked_at IS NULL AND lp_group IN " +
            "<foreach item='item' collection='groups' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    int isAnyGroupAuthorizer(@Param("accountId") int accountId,
                             @Param("groups") Collection<String> groups);
}
