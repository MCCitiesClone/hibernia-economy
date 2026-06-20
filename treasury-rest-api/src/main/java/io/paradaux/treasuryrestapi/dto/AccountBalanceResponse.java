package io.paradaux.treasuryrestapi.dto;

/**
 * Response body for GET /accounts/{accountId}/balance.
 * {@code balance} is a decimal string to avoid IEEE 754 precision loss.
 */
public record AccountBalanceResponse(long accountId, String balance) {}
