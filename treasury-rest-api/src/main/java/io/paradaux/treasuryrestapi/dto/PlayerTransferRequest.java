package io.paradaux.treasuryrestapi.dto;

/**
 * Request body for {@code POST /api/v1/transfers/to-player}.
 *
 * <p>The recipient is identified by <b>exactly one</b> of:
 * <ul>
 *   <li>{@code toPlayerUuid} — the player's Mojang UUID, or</li>
 *   <li>{@code toPlayerName} — their in-game name (case-insensitive, resolved
 *       via the {@code economy_players} name cache).</li>
 * </ul>
 * The server resolves it to the player's PERSONAL account before transferring.
 *
 * <p>{@code fromAccountId} follows the same rules as {@link TransferRequest}:
 * required for BUSINESS keys, ignored for PERSONAL/GOVERNMENT (the source is the
 * token's {@code acc} claim).
 */
public record PlayerTransferRequest(Long fromAccountId, String toPlayerUuid, String toPlayerName,
                                    String amount, String memo) {}
