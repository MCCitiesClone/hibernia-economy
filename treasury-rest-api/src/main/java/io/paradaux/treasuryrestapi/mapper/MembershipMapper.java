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
     * <p>The {@code account_members} table is soft-deleted: removed members
     * keep the row with {@code left_at} set. Filter on {@code left_at IS
     * NULL} so a removed member can no longer access account history via
     * this REST API.
     */
    @Select("SELECT COUNT(*) FROM account_members " +
            "WHERE account_id = #{accountId} " +
            "  AND member_uuid_bin = #{memberUuid} " +
            "  AND left_at IS NULL")
    int isMember(@Param("accountId") long accountId, @Param("memberUuid") UUID memberUuid);
}
