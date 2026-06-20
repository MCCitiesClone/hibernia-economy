package io.paradaux.business.api;

import java.util.UUID;

/**
 * Employment offer operations.
 *
 * <p>Methods may throw {@link RuntimeException} subclasses for invalid operations
 * (e.g., firm not found, no pending offer, insufficient permissions).
 */
public interface RequestApi {

    /**
     * Offers employment to a player at a firm.
     *
     * @param firmId   the firm ID
     * @param playerId the player's UUID
     * @param actorId  UUID of the player making the offer
     */
    void offerEmployment(int firmId, UUID playerId, UUID actorId);

    /**
     * Rescinds a pending employment offer.
     *
     * @param firmId   the firm ID
     * @param playerId the player whose offer is being rescinded
     * @param actorId  UUID of the player rescinding the offer
     */
    void rescindOffer(int firmId, UUID playerId, UUID actorId);

    /**
     * Accepts a pending employment offer.
     *
     * @param firmId   the firm ID
     * @param playerId the player accepting the offer
     */
    void acceptOffer(int firmId, UUID playerId);

    /**
     * Rejects a pending employment offer.
     *
     * @param firmId   the firm ID
     * @param playerId the player rejecting the offer
     */
    void rejectOffer(int firmId, UUID playerId);
}
