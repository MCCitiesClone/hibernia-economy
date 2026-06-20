package io.paradaux.treasuryrestapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Global market totals over {@code chestshop_sale} (full-table aggregate). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChestShopMarketStatsRow {
    private long totalSales;
    private BigDecimal totalVolume;
    private long distinctItems;
}
