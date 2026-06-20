package io.paradaux.business.api;

import io.paradaux.business.model.FirmPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Player lookup operations.
 */
public interface PlayerApi {

    /**
     * Finds a player by their UUID.
     *
     * @param uuid the player's UUID
     * @return the player, or empty if not found
     */
    Optional<FirmPlayer> findByUuid(UUID uuid);

    /**
     * Finds a player by their current name.
     *
     * @param name the player's name
     * @return the player, or empty if not found
     */
    Optional<FirmPlayer> findByName(String name);

    /**
     * Searches for players whose names start with the given prefix.
     *
     * @param prefix the name prefix to search for
     * @param limit  maximum number of results
     * @return list of matching players
     */
    List<FirmPlayer> searchByPrefix(String prefix, int limit);
}
