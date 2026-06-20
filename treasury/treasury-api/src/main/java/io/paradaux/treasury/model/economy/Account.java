package io.paradaux.treasury.model.economy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Account {
    private Integer accountId;
    private AccountType accountType;
    private UUID ownerUuid;
    private String displayName;
    private boolean requiresAuthorization;
    private boolean isArchived;
    private boolean allowOverdraft;
    private BigDecimal creditLimit;
}
