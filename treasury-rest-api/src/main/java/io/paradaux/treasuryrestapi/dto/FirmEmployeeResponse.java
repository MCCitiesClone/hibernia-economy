package io.paradaux.treasuryrestapi.dto;

import java.time.Instant;

/**
 * A single entry in the GET /firms/me/employees list.
 * {@code playerName} is the player's current IGN; null if not yet seen on the server.
 */
public record FirmEmployeeResponse(
        String playerUuid,
        String playerName,
        String roleName,
        Instant joinedAt
) {}
