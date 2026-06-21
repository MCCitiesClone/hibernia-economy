package io.paradaux.treasuryrestapi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

@Mapper
public interface MembershipMapper {

    /**
     * Returns a count > 0 if the given UUID is currently a member of the
     * given account. Used by the transaction history endpoint to allow
     * business-account members to view history beyond their own account.
     *
     * <p>Access lives in the consolidated {@code account_access} table (PAR-249),
     * one row per (account, subject) on the ordered scale VIEWER &lt; MEMBER &lt;
     * AUTHORIZER. "Member" means level MEMBER or AUTHORIZER (an authorizer is a
     * member); a read-only VIEWER does not count. Soft-deleted via
     * {@code removed_at}, so a revoked member can no longer access history here.
     */
    @Select("SELECT COUNT(*) FROM account_access " +
            "WHERE account_id = #{accountId} " +
            "  AND subject_uuid_bin = #{memberUuid} " +
            "  AND level IN ('MEMBER','AUTHORIZER') " +
            "  AND removed_at IS NULL")
    int isMember(@Param("accountId") long accountId, @Param("memberUuid") UUID memberUuid);
}
