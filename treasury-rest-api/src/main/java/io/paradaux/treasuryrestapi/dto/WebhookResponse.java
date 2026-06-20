package io.paradaux.treasuryrestapi.dto;

import java.time.Instant;

/**
 * A webhook subscription as returned by list/get. The signing secret is NOT
 * included here — it is shown once, only in {@link CreateWebhookResponse}.
 *
 * @param scope "account" or "firm"
 */
public record WebhookResponse(long id,
                              String scope,
                              Long accountId,
                              Long firmId,
                              String url,
                              boolean active,
                              long consecutiveFailures,
                              Instant createdAt) {}
