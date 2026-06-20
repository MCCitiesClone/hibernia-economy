package io.paradaux.treasuryrestapi.dto;

import java.util.List;

/** A page of live shops. */
public record ChestShopShopsResponse(int page,
                                     int totalPages,
                                     long totalItems,
                                     List<ChestShopShopResponse> items) {}
