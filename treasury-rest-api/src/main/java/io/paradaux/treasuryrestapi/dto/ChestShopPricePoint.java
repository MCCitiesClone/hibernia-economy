package io.paradaux.treasuryrestapi.dto;

/** One day on an item's price/volume time series. */
public record ChestShopPricePoint(String day,
                                  long sales,
                                  long totalQuantity,
                                  String totalVolume,
                                  String avgUnitPrice) {}
