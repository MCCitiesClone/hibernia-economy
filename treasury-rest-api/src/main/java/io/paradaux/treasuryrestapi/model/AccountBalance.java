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
public class AccountBalance {
    private long accountId;
    private BigDecimal balance;
    /** Optimistic concurrency version — incremented on every balance update. */
    private long version;
}
