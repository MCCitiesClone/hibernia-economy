package io.paradaux.treasuryrestapi.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A per-issuer rate-limit multiplier override (api_rate_limit_override). */
@Data
public class RateLimitOverride {
    private UUID ownerUuid;
    private BigDecimal multiplier;
    private String note;
    private LocalDateTime updatedAt;
}
