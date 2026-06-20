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
public class FirmAccountSummary {
    private long accountId;
    private String displayName;
    private String accountType;
    private boolean archived;
    private BigDecimal balance;
}
