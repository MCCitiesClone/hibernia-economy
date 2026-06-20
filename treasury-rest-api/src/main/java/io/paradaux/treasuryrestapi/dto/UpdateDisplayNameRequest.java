package io.paradaux.treasuryrestapi.dto;

/**
 * Request body for PATCH /firms/me/accounts/{accountId}/display-name.
 */
public record UpdateDisplayNameRequest(String displayName) {}
