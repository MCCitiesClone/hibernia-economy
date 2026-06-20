package io.paradaux.treasuryrestapi.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A registered webhook endpoint (row of {@code webhook_subscription}). Scoped to
 * the API key that created it: PERSONAL/GOVERNMENT keys carry an {@code accountId};
 * BUSINESS keys carry a {@code firmId} (all the firm's accounts).
 */
@Data
public class WebhookSubscription {
    private long subscriptionId;
    /** Null for owner-scoped subscriptions managed via economy-explorer (no API key). */
    private Long apiKeyId;
    private UUID ownerUuid;          // owner_uuid_bin (aliased in SELECTs)
    private String keyType;          // PERSONAL | BUSINESS | GOVERNMENT
    private Integer accountId;       // scope for PERSONAL/GOVERNMENT, else null
    private Integer firmId;          // scope for BUSINESS, else null
    private String targetUrl;
    private String secret;           // HMAC-SHA256 signing key (hex)
    private boolean active;
    private long consecutiveFailures;
    private LocalDateTime disabledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
