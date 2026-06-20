package io.paradaux.treasuryrestapi.dto;

import java.time.Instant;

/**
 * One live shop sign. Money fields are plain-string decimals; {@code buyPrice}/
 * {@code sellPrice} are null when that side isn't offered. {@code currentStock}
 * is null for admin/infinite shops. {@code ownerName} is null for admin shops.
 */
public record ChestShopShopResponse(long shopId,
                                    String world,
                                    Integer x,
                                    Integer y,
                                    Integer z,
                                    boolean adminShop,
                                    String accountType,
                                    Integer firmId,
                                    String ownerUuid,
                                    String ownerName,
                                    String material,
                                    String itemKey,
                                    String itemName,
                                    boolean itemCustom,
                                    String buyPrice,
                                    String sellPrice,
                                    int batchQty,
                                    Integer currentStock,
                                    Instant stockAt,
                                    Instant lastSeen) {}
