package io.paradaux.treasuryrestapi.dto;

import java.time.Instant;

public record TransactionItem(long postingId,
                              long txnId,
                              String amount,
                              String memo,
                              String message,
                              Instant settledAt,
                              String initiatorUuid,
                              String pluginSystem) {}
