package io.paradaux.treasury.api.exceptions;

import java.util.UUID;

/**
 * Thrown by {@code issueFine} when the targeted player's account doesn't have
 * enough funds to cover the fine. Wraps the underlying {@link IllegalStateException}
 * raised by the ledger service so handlers can render an i18n message without
 * scraping the message text.
 */
public class InsufficientFineFundsException extends TreasuryException {
    private final UUID playerUuid;

    public InsufficientFineFundsException(UUID playerUuid, Throwable cause) {
        super("Player " + playerUuid + " has insufficient funds to cover the fine", cause);
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() { return playerUuid; }
}
