package io.paradaux.treasuryrestapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One day's aggregated trade activity for an item (price time-series point). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChestShopPriceDayRow {
    private String day;
    private long sales;
    private long totalQuantity;
    private BigDecimal totalVolume;
    private BigDecimal avgUnitPrice;
}
