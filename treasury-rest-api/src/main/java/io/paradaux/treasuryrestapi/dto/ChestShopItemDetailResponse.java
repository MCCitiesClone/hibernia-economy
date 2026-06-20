package io.paradaux.treasuryrestapi.dto;

import java.util.List;

/**
 * Per-item market detail: windowed stats, the cheapest live shops currently
 * selling it, and a daily price/volume series over the same window.
 */
public record ChestShopItemDetailResponse(String itemKey,
                                          String material,
                                          String itemName,
                                          boolean itemCustom,
                                          int windowDays,
                                          long tradeCount,
                                          long totalQuantity,
                                          String totalVolume,
                                          String avgUnitPrice,
                                          long activeShopCount,
                                          List<ChestShopShopResponse> cheapestShops,
                                          List<ChestShopPricePoint> priceByDay) {}
