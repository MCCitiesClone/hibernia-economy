package io.paradaux.treasury.model.economy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalanceEntry {
    private int accountId;
    private UUID ownerUuid;
    private String displayName;
    private BigDecimal balance;
}
