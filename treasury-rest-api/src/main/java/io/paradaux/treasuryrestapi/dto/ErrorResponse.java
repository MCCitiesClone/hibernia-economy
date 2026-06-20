package io.paradaux.treasuryrestapi.dto;

/**
 * Common error envelope returned by all error responses.
 * The {@code error} field is machine-readable and stable; {@code message} is informational.
 */
public record ErrorResponse(String error, String message) {}
