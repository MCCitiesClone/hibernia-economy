package io.paradaux.treasuryrestapi.dto;

/** Request body for {@code POST /api/v1/webhooks}. */
public record CreateWebhookRequest(String url) {}
