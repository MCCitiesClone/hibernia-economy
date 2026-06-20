package io.paradaux.treasuryrestapi.dto;

import java.time.Instant;

/**
 * Response body for GET /firms/{firmName}.
 * Accessible to any authenticated caller.
 * {@code defaultAccountId} is null if the firm has no default account configured.
 */
public record PublicFirmResponse(
        long firmId,
        String displayName,
        String discordUrl,
        String hqRegion,
        Long defaultAccountId,
        boolean archived,
        Instant createdAt
) {}
