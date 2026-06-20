package io.paradaux.treasuryrestapi.dto;

/** Global ChestShop market totals. */
public record ChestShopMarketStatsResponse(long totalSales,
                                           String totalVolume,
                                           long distinctItems,
                                           long activeShops) {}
