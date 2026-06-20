package io.paradaux.treasuryrestapi.dto;

/** Admin firm-details update (HQ region / Discord URL). Null = unchanged, blank = clear. */
public record FirmDetailsRequest(String discordUrl, String hqRegion) {}
