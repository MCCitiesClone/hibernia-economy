package io.paradaux.treasuryrestapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTxn {
    private long txnId;
    private String message;
    private LocalDateTime settlementTime;
    /** Stored as BINARY(16) in the DB — mapped via UuidTypeHandler. */
    private UUID initiatorUuidBin;
    private String pluginSystem;
    /** SHA-256 hex of the client-supplied Idempotency-Key header. Nullable. */
    private String clientDedupKey;
}
