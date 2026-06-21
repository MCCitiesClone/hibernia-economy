package io.paradaux.treasury.mappers;

import io.paradaux.treasury.model.economy.AccountMember;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Membership and authoriser CRUD on {@code account_members} and
 * {@code account_authorizers}.
 *
 * <p><b>Soft-delete semantics.</b> Removing a member or revoking an
 * authoriser sets the {@code left_at} / {@code revoked_at} column instead
 * of deleting the row, so the audit trail of historical access is
 * preserved. Reads filter on {@code … IS NULL} so consumers see only
 * currently-active rows. Re-adding a previously-removed member/authoriser
 * uses {@code INSERT … ON DUPLICATE KEY UPDATE} to clear the timestamp
 * rather than insert a duplicate row.
 */
@Mapper
public interface MembershipMapper {

    @Select("""
      SELECT COUNT(*) FROM account_authorizers
       WHERE account_id = #{accountId}
         AND authorizer_uuid_bin = #{authorizer}
         AND revoked_at IS NULL
      """)
    int isAuthorizer(@Param("accountId") int accountId,
                     @Param("authorizer") UUID authorizer);

    @Select("""
      SELECT COUNT(*) FROM account_members
       WHERE account_id = #{accountId}
         AND member_uuid_bin = #{member}
         AND left_at IS NULL
      """)
    int isMember(@Param("accountId") int accountId,
                 @Param("member") UUID member);

    // ---- Member CRUD ----

    @Insert("""
      INSERT INTO account_members(account_id, member_uuid_bin, added_by_uuid_bin)
      VALUES(#{accountId}, #{memberUuid}, #{addedByUuid})
      ON DUPLICATE KEY UPDATE
          left_at = NULL,
          added_by_uuid_bin = VALUES(added_by_uuid_bin)
      """)
    int addMember(@Param("accountId") int accountId,
                  @Param("memberUuid") UUID memberUuid,
                  @Param("addedByUuid") UUID addedByUuid);

    /**
     * Soft-removes the member by stamping {@code left_at}; the row stays
     * for audit. Subsequent {@link #addMember} on the same (account,
     * member) pair clears {@code left_at} via ON DUPLICATE KEY UPDATE.
     */
    @Update("""
      UPDATE account_members
         SET left_at = CURRENT_TIMESTAMP
       WHERE account_id = #{accountId}
         AND member_uuid_bin = #{memberUuid}
         AND left_at IS NULL
      """)
    int removeMember(@Param("accountId") int accountId,
                     @Param("memberUuid") UUID memberUuid);

    @Select("""
      SELECT account_id, member_uuid_bin, added_by_uuid_bin, created_at
        FROM account_members
       WHERE account_id = #{accountId}
         AND left_at IS NULL
       ORDER BY created_at
      """)
    @Results(id = "memberMap", value = {
            @Result(column = "account_id", property = "accountId"),
            @Result(column = "member_uuid_bin", property = "memberUuid"),
            @Result(column = "added_by_uuid_bin", property = "addedByUuid"),
            @Result(column = "created_at", property = "createdAt", javaType = Instant.class)
    })
    List<AccountMember> getMembers(@Param("accountId") int accountId);

    // ---- Authorizer CRUD ----

    @Insert("""
      INSERT INTO account_authorizers(account_id, authorizer_uuid_bin, added_by_uuid_bin)
      VALUES(#{accountId}, #{authorizerUuid}, #{addedByUuid})
      ON DUPLICATE KEY UPDATE
          revoked_at = NULL,
          added_by_uuid_bin = VALUES(added_by_uuid_bin)
      """)
    int addAuthorizer(@Param("accountId") int accountId,
                      @Param("authorizerUuid") UUID authorizerUuid,
                      @Param("addedByUuid") UUID addedByUuid);

    /**
     * Soft-revokes the authoriser by stamping {@code revoked_at}; the row
     * stays for audit.
     */
    @Update("""
      UPDATE account_authorizers
         SET revoked_at = CURRENT_TIMESTAMP
       WHERE account_id = #{accountId}
         AND authorizer_uuid_bin = #{authorizerUuid}
         AND revoked_at IS NULL
      """)
    int removeAuthorizer(@Param("accountId") int accountId,
                         @Param("authorizerUuid") UUID authorizerUuid);

    @Select("""
      SELECT account_id, authorizer_uuid_bin, added_by_uuid_bin, created_at
        FROM account_authorizers
       WHERE account_id = #{accountId}
         AND revoked_at IS NULL
       ORDER BY created_at
      """)
    @Results(id = "authorizerMap", value = {
            @Result(column = "account_id", property = "accountId"),
            @Result(column = "authorizer_uuid_bin", property = "memberUuid"),
            @Result(column = "added_by_uuid_bin", property = "addedByUuid"),
            @Result(column = "created_at", property = "createdAt", javaType = Instant.class)
    })
    List<AccountMember> getAuthorizers(@Param("accountId") int accountId);

    // ---- Viewer CRUD (read-only access tier, PAR-237) ----

    @Select("""
      SELECT COUNT(*) FROM account_viewers
       WHERE account_id = #{accountId}
         AND viewer_uuid_bin = #{viewer}
         AND left_at IS NULL
      """)
    int isViewer(@Param("accountId") int accountId,
                 @Param("viewer") UUID viewer);

    @Insert("""
      INSERT INTO account_viewers(account_id, viewer_uuid_bin, added_by_uuid_bin)
      VALUES(#{accountId}, #{viewerUuid}, #{addedByUuid})
      ON DUPLICATE KEY UPDATE
          left_at = NULL,
          added_by_uuid_bin = VALUES(added_by_uuid_bin)
      """)
    int addViewer(@Param("accountId") int accountId,
                  @Param("viewerUuid") UUID viewerUuid,
                  @Param("addedByUuid") UUID addedByUuid);

    @Update("""
      UPDATE account_viewers
         SET left_at = CURRENT_TIMESTAMP
       WHERE account_id = #{accountId}
         AND viewer_uuid_bin = #{viewerUuid}
         AND left_at IS NULL
      """)
    int removeViewer(@Param("accountId") int accountId,
                     @Param("viewerUuid") UUID viewerUuid);

    @Select("""
      SELECT account_id, viewer_uuid_bin, added_by_uuid_bin, created_at
        FROM account_viewers
       WHERE account_id = #{accountId}
         AND left_at IS NULL
       ORDER BY created_at
      """)
    @Results(id = "viewerMap", value = {
            @Result(column = "account_id", property = "accountId"),
            @Result(column = "viewer_uuid_bin", property = "memberUuid"),
            @Result(column = "added_by_uuid_bin", property = "addedByUuid"),
            @Result(column = "created_at", property = "createdAt", javaType = Instant.class)
    })
    List<AccountMember> getViewers(@Param("accountId") int accountId);
}
