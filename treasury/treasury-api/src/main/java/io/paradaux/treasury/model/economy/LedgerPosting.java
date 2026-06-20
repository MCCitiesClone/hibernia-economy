package io.paradaux.treasury.model.economy;

import java.math.BigDecimal;

public record LedgerPosting(
        Long postingId,
        long txnId,
        int accountId,
        BigDecimal amount,
        String memo
) {}
