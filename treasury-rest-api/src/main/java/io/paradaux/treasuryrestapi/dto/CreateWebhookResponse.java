package io.paradaux.treasuryrestapi.dto;

/**
 * Response for {@code POST /api/v1/webhooks}. The {@code secret} is the
 * HMAC-SHA256 signing key for verifying the {@code X-Treasury-Signature}
 * header on deliveries — it is returned <b>once, here only</b>, and never
 * again. Store it securely.
 */
public record CreateWebhookResponse(long id,
                                    String scope,
                                    Long accountId,
                                    Long firmId,
                                    String url,
                                    boolean active,
                                    String secret) {}
