package io.paradaux.treasury.model.economy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GovernmentFine {
    private long       fineId;
    private UUID       playerUuid;
    private int        govAccountId;
    private BigDecimal amount;
    private String     reason;
    private long       txnId;
    private UUID       issuedBy;
    private Instant    issuedAt;
    private boolean    revoked;
    private UUID       revokedBy;
    private Long       revokeTxnId;
    private Instant    revokedAt;
}
