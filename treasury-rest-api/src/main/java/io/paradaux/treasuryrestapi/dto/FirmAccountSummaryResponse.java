package io.paradaux.treasuryrestapi.dto;

/**
 * A single entry in the GET /firms/me/accounts list.
 * {@code balance} is a decimal string to match the rest of the API.
 * {@code displayName} may be null if the account has none set.
 */
public record FirmAccountSummaryResponse(
        long accountId,
        String displayName,
        String accountType,
        String balance,
        boolean archived
) {}
