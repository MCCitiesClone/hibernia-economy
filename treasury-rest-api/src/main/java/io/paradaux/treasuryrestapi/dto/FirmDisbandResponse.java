package io.paradaux.treasuryrestapi.dto;

import java.util.List;

/**
 * Result of an admin firm disband: the firm's final state plus a per-account
 * breakdown of what happened (balance swept to the proprietor, account archived).
 * The caller (economy-explorer) persists this into its audit trail.
 */
public record FirmDisbandResponse(
        long firmId,
        String displayName,
        boolean archived,
        List<DisbandedAccount> accounts
) {
    /**
     * One firm account's disband outcome. {@code sweptAmount}/{@code toAccountId} are
     * null when the account had no positive balance to move.
     */
    public record DisbandedAccount(
            long accountId,
            String sweptAmount,
            Long toAccountId,
            boolean archived
    ) {}
}
