package io.paradaux.treasuryrestapi.dto;

import java.util.List;

/** Response for {@code GET /api/v1/webhooks}. */
public record WebhookListResponse(List<WebhookResponse> webhooks) {}
