package io.paradaux.treasuryrestapi.dto;

/**
 * Request body for PATCH /firms/me.
 * All fields are optional — only non-null values are applied.
 * To clear {@code discordUrl} or {@code hqRegion}, pass an empty string {@code ""}.
 */
public record FirmUpdateRequest(String discordUrl, String hqRegion) {}
