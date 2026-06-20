package io.paradaux.treasuryrestapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Aggregate market row for one item key, grouped from {@code chestshop_sale}.
 * {@code material}/{@code itemName}/{@code itemCustom} are {@code MAX(...)} over
 * the group (item_key is the stable identity; the label is informational).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChestShopItemRow {
    private String itemKey;
    private String material;
    private String itemName;
    private boolean itemCustom;
    private long tradeCount;
    private long totalQuantity;
    private BigDecimal totalVolume;
}
