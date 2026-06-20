package io.paradaux.treasuryrestapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Per-item summary for the item-detail endpoint. {@code tradeCount}/{@code
 * totalQuantity}/{@code totalVolume} are over the requested window; {@code
 * allTimeTrades} is the lifetime count, used to tell "no recent activity" from
 * "no such item" (404).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChestShopItemStatsRow {
    private String itemKey;
    private String material;
    private String itemName;
    private boolean itemCustom;
    private long tradeCount;
    private long totalQuantity;
    private BigDecimal totalVolume;
    private long allTimeTrades;
}
