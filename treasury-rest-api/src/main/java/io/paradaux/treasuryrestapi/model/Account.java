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
public class Account {
    private long accountId;
    private boolean archived;
    /** If true, transfers require an in-game authorizer; blocked at the REST API layer (MVP). */
    private boolean requiresAuthorization;
    private boolean allowOverdraft;
    /** The maximum overdraft allowed. Only meaningful when allowOverdraft is true. */
    private BigDecimal creditLimit;
}
