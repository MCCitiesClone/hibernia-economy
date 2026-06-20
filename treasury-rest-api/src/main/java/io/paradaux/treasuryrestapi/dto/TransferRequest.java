package io.paradaux.treasuryrestapi.dto;

/**
 * Request body for POST /transfers.
 * <ul>
 *   <li>{@code fromAccountId} — required for BUSINESS keys (must be an account owned by the firm);
 *       ignored for PERSONAL and GOVERNMENT keys, which always debit the {@code acc} JWT claim.</li>
 *   <li>{@code amount} — decimal string to avoid IEEE 754 precision loss.</li>
 *   <li>{@code memo} — optional, max 255 chars.</li>
 * </ul>
 */
public record TransferRequest(Long fromAccountId, Long toAccountId, String amount, String memo) {}
