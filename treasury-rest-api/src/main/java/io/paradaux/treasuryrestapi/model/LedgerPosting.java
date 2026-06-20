package io.paradaux.treasuryrestapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerPosting {
    private long postingId;
    private long txnId;
    private long accountId;
    /** Negative = debit, positive = credit. */
    private BigDecimal amount;
    private String memo;
}
