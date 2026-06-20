package io.paradaux.treasuryrestapi.dto;

import java.util.List;

/** A page of item aggregates. */
public record ChestShopItemsResponse(int page,
                                     int totalPages,
                                     long totalItems,
                                     List<ChestShopItemResponse> items) {}
