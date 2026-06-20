package io.paradaux.treasuryrestapi.dto;

/**
 * Request body for {@code PATCH /api/v1/webhooks/{id}}. Both fields optional:
 * a null leaves the current value unchanged.
 */
public record UpdateWebhookRequest(String url, Boolean active) {}
