package io.paradaux.treasuryrestapi.dto;

import java.time.Instant;

public record TransferResponse(long txnId,
                               long fromAccountId,
                               long toAccountId,
                               String amount,
                               String memo,
                               Instant settledAt) {}
