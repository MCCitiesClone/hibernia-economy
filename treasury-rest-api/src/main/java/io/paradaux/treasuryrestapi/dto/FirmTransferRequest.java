package io.paradaux.treasuryrestapi.dto;

/**
 * Request body for POST /transfers/to-firm.
 * Resolves the destination to the named firm's default account automatically.
 * {@code fromAccountId} follows the same rules as {@link TransferRequest}:
 * required for BUSINESS keys, ignored for PERSONAL/GOVERNMENT (uses the {@code acc} JWT claim).
 */
public record FirmTransferRequest(Long fromAccountId, String toFirm, String amount, String memo) {}
