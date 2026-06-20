package io.paradaux.treasuryrestapi.dto;

/**
 * Response body for GET /firms/me and PATCH /firms/me.
 * {@code discordUrl} and {@code hqRegion} may be null if not set.
 */
public record FirmResponse(
        long firmId,
        String displayName,
        String discordUrl,
        String hqRegion,
        boolean archived
) {}
