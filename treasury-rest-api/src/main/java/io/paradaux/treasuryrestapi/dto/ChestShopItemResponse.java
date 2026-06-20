package io.paradaux.treasuryrestapi.dto;

/** Aggregate market totals for one item key (all-time). */
public record ChestShopItemResponse(String itemKey,
                                    String material,
                                    String itemName,
                                    boolean itemCustom,
                                    long tradeCount,
                                    long totalQuantity,
                                    String totalVolume) {}
