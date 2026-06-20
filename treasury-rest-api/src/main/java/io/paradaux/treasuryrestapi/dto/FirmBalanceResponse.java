package io.paradaux.treasuryrestapi.dto;

/**
 * Response body for GET /firms/{firmName}/balance.
 * {@code totalBalance} is the sum of all account balances owned by the firm,
 * expressed as a decimal string to avoid IEEE 754 precision loss.
 */
public record FirmBalanceResponse(long firmId, String displayName, String totalBalance) {}
