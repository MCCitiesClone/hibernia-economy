package io.paradaux.treasury.model.economy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LedgerTxn {
    private long txnId;
    private Instant tradeTime;
    private Instant settlementTime;
    private String message;
    private UUID initiatorUuid;
    private UUID authorizerUuid;
    private String pluginSystem;
    private byte[] clientDedupKey;
}
