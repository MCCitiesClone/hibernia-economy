package io.paradaux.treasury.model.economy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountBalance {
    private int accountId;
    private BigDecimal balance;
    private long version;
}
