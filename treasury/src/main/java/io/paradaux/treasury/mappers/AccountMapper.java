package io.paradaux.treasury.mappers;

import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountBalance;
import io.paradaux.treasury.model.economy.AccountType;
import io.paradaux.treasury.model.economy.AccountTypeTotal;
import io.paradaux.treasury.model.economy.BalanceEntry;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

@Mapper
public interface AccountMapper {

    // ---- lookups ----
    @Select("""
            SELECT account_id, account_type, owner_uuid_bin, display_name,
                   requires_authorization, is_archived, allow_overdraft, credit_limit
              FROM accounts
             WHERE account_id = #{accountId}
            """)
    @Results(id = "accountMap", value = {
            @Result(column = "account_id", property = "accountId", id = true),
            @Result(column = "account_type", property = "accountType"),
            @Result(column = "owner_uuid_bin", property = "ownerUuid"),
            @Result(column = "display_name", property = "displayName"),
            @Result(column = "requires_authorization", property = "requiresAuthorization"),
            @Result(column = "is_archived", property = "isArchived"),
            @Result(column = "allow_overdraft", property = "allowOverdraft"),
            @Result(column = "credit_limit", property = "creditLimit")
    })
    Account findById(@Param("accountId") int accountId);

    @Select("""
      SELECT a.account_id, a.account_type, a.owner_uuid_bin, a.display_name,
             a.requires_authorization, a.is_archived, a.allow_overdraft, a.credit_limit
        FROM accounts a
       WHERE a.account_type = 'SYSTEM' AND a.display_name = #{pluginName}
       LIMIT 1
      """)
    @ResultMap("accountMap")
    Account findSystemAccountForPlugin(@Param("pluginName") String pluginName);

    @Select("""
            SELECT account_id
              FROM accounts
             WHERE account_type = 'PERSONAL'
               AND owner_uuid_bin = #{ownerUuid}
             LIMIT 1
            """)
    Integer findPersonalAccountId(@Param("ownerUuid") UUID ownerUuid);

    @Select("""
            SELECT account_id, account_type, owner_uuid_bin, display_name,
                   requires_authorization, is_archived, allow_overdraft, credit_limit
              FROM accounts
             WHERE owner_uuid_bin = #{ownerUuid}
            """)
    @ResultMap("accountMap")
    List<Account> findAccountsByOwner(@Param("ownerUuid") UUID ownerUuid);

    @Select("""
        SELECT account_id, account_type, owner_uuid_bin, display_name,
               requires_authorization, is_archived, allow_overdraft, credit_limit
          FROM accounts
         WHERE account_type = 'GOVERNMENT'
           AND display_name = #{name}
         LIMIT 1
        """)
    @ResultMap("accountMap")
    Account findGovernmentAccountByName(@Param("name") String name);

    @Select("""
        SELECT COUNT(*) > 0
          FROM accounts
         WHERE account_type = 'GOVERNMENT'
           AND display_name = #{name}
           AND is_archived = 0
        """)
    boolean existsGovernmentAccountByName(@Param("name") String name);

    @Select("""
        SELECT account_id, account_type, owner_uuid_bin, display_name,
               requires_authorization, is_archived, allow_overdraft, credit_limit
          FROM accounts
         WHERE account_type = #{accountType}
           AND owner_uuid_bin = #{ownerUuid}
        """)
    @ResultMap("accountMap")
    List<Account> findAccountsByTypeAndOwner(@Param("accountType") AccountType accountType,
                                            @Param("ownerUuid") UUID ownerUuid);

    @Select("""
        SELECT DISTINCT a.account_id, a.account_type, a.owner_uuid_bin, a.display_name,
               a.requires_authorization, a.is_archived, a.allow_overdraft, a.credit_limit
          FROM accounts a
          JOIN account_access am ON a.account_id = am.account_id
         WHERE am.subject_uuid_bin = #{memberUuid}
           AND am.level IN ('MEMBER','AUTHORIZER') AND am.removed_at IS NULL
        """)
    @ResultMap("accountMap")
    List<Account> findAccountsByMember(@Param("memberUuid") UUID memberUuid);

    // ---- inserts ----

    // generic insert (one param -> MyBatis can set generated key)
    @Insert("""
            INSERT INTO accounts(
                account_type,
                owner_uuid_bin,
                display_name,
                requires_authorization,
                is_archived,
                allow_overdraft,
                credit_limit
            )
            VALUES(
                #{accountType},
                #{ownerUuid},
                #{displayName},
                #{requiresAuthorization},
                #{isArchived},
                #{allowOverdraft},
                #{creditLimit}
            )
            """)
    @Options(useGeneratedKeys = true,
            keyProperty = "accountId",
            keyColumn = "account_id")
    int insertAccount(Account account);

    @Insert("""
            INSERT INTO account_balances_mat(account_id, balance, version)
            VALUES(#{accountId}, 0.00, 0)
            """)
    int seedBalance(@Param("accountId") int accountId);

    @Select("""
            SELECT account_id, balance, version
              FROM account_balances_mat
             WHERE account_id = #{accountId}
             FOR UPDATE
            """)
    @Results(id = "balanceMap", value = {
            @Result(column = "account_id", property = "accountId", id = true),
            @Result(column = "balance", property = "balance"),
            @Result(column = "version", property = "version")
    })
    AccountBalance lockBalance(@Param("accountId") int accountId);

    /** Non-locking balance read for read-only API calls. */
    @Select("""
            SELECT account_id, balance, version
              FROM account_balances_mat
             WHERE account_id = #{accountId}
            """)
    @ResultMap("balanceMap")
    AccountBalance readBalance(@Param("accountId") int accountId);

    /** Loads several accounts in one round-trip (used by transfer for from+to). */
    @Select("""
            <script>
            SELECT account_id, account_type, owner_uuid_bin, display_name,
                   requires_authorization, is_archived, allow_overdraft, credit_limit
              FROM accounts
             WHERE account_id IN
             <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    @ResultMap("accountMap")
    List<Account> findByIds(@Param("ids") List<Integer> ids);

    /**
     * Locks several balance rows FOR UPDATE in one round-trip. {@code ORDER BY
     * account_id} (over the PK) makes lock acquisition ascending, so combined with
     * ascending-ordered posting inserts no concurrent A→B / B→A pair can deadlock.
     */
    @Select("""
            <script>
            SELECT account_id, balance, version
              FROM account_balances_mat
             WHERE account_id IN
             <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>
             ORDER BY account_id
             FOR UPDATE
            </script>
            """)
    @ResultMap("balanceMap")
    List<AccountBalance> lockBalances(@Param("ids") List<Integer> ids);

    @Update("""
            UPDATE accounts
               SET display_name            = #{displayName},
                   requires_authorization  = #{requiresAuthorization},
                   is_archived             = #{isArchived},
                   allow_overdraft         = #{allowOverdraft},
                   credit_limit            = #{creditLimit}
             WHERE account_id = #{accountId}
            """)
    int updateAccount(Account account);

    /**
     * Reassigns an account's owner. Kept separate from {@link #updateAccount}
     * so a general field update can never accidentally move ownership. Used to
     * hand a BUSINESS firm account to a new proprietor on transfer.
     */
    @Update("""
            UPDATE accounts
               SET owner_uuid_bin = #{ownerUuid}
             WHERE account_id = #{accountId}
            """)
    int updateOwner(@Param("accountId") int accountId, @Param("ownerUuid") UUID ownerUuid);

    // ---- baltop ----

    @Select("""
            SELECT a.account_id, a.owner_uuid_bin, a.display_name, b.balance
              FROM accounts a
              JOIN account_balances_mat b ON a.account_id = b.account_id
             WHERE a.account_type = 'PERSONAL' AND a.is_archived = 0
             ORDER BY b.balance DESC
             LIMIT #{limit} OFFSET #{offset}
            """)
    @Results(id = "balanceEntryMap", value = {
            @Result(column = "account_id", property = "accountId", id = true),
            @Result(column = "owner_uuid_bin", property = "ownerUuid"),
            @Result(column = "display_name", property = "displayName"),
            @Result(column = "balance", property = "balance")
    })
    List<BalanceEntry> getTopBalances(@Param("limit") int limit, @Param("offset") int offset);

    // ---- economy summary ----

    /**
     * Total balance per account type for the active (non-archived) economy,
     * excluding SYSTEM. Mirrors the explorer's total-supply scope so the
     * in-game {@code /economy} figures match the UI.
     */
    @Select("""
            SELECT a.account_type AS account_type, COALESCE(SUM(b.balance), 0) AS total
              FROM accounts a
              JOIN account_balances_mat b ON a.account_id = b.account_id
             WHERE a.is_archived = 0
               AND a.account_type IN ('PERSONAL', 'BUSINESS', 'GOVERNMENT')
             GROUP BY a.account_type
            """)
    @Results({
            @Result(column = "account_type", property = "accountType"),
            @Result(column = "total", property = "total")
    })
    List<AccountTypeTotal> getEconomyTotalsByType();

    @Select("SELECT COUNT(*) FROM accounts WHERE account_type = 'PERSONAL' AND is_archived = 0")
    int countPersonalAccounts();

    @Select("""
        SELECT account_id, account_type, owner_uuid_bin, display_name,
               requires_authorization, is_archived, allow_overdraft, credit_limit
          FROM accounts
         WHERE account_type = 'GOVERNMENT' AND is_archived = 0
         ORDER BY display_name ASC
        """)
    @ResultMap("accountMap")
    List<Account> findAllGovernmentAccounts();

    @Select("""
        SELECT account_id, account_type, owner_uuid_bin, display_name,
               requires_authorization, is_archived, allow_overdraft, credit_limit
          FROM accounts
         WHERE account_type = 'BUSINESS'
           AND display_name = #{name}
           AND is_archived = 0
         LIMIT 1
        """)
    @ResultMap("accountMap")
    Account findBusinessAccountByName(@Param("name") String name);

}