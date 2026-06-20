package io.paradaux.treasuryrestapi.controller;

import io.paradaux.treasuryrestapi.dto.ChestShopItemDetailResponse;
import io.paradaux.treasuryrestapi.dto.ChestShopItemsResponse;
import io.paradaux.treasuryrestapi.dto.ChestShopMarketStatsResponse;
import io.paradaux.treasuryrestapi.dto.ChestShopShopsResponse;
import io.paradaux.treasuryrestapi.ratelimit.RateLimit;
import io.paradaux.treasuryrestapi.service.ChestShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only ChestShop market data — the same aggregate/directory views
 * economy-explorer renders, exposed as a hammer-friendly REST surface for
 * external consumers (price trackers, shop finders, dashboards).
 *
 * <p>These endpoints are intentionally <b>unauthenticated</b>: they carry no
 * per-entity financial detail, only public market aggregates and the live
 * "what's for sale where" registry. {@code JwtAuthFilter} skips this prefix so
 * no Bearer token is required; callers are therefore throttled by client IP via
 * {@link RateLimit#anonymousPerMinute()} (the personal/business limits never
 * apply here because there is never a verified principal).
 *
 * <p>Load posture: the aggregate endpoints are cached in the service; the shop
 * directory is a single index-served table with a hard page cap. Anonymous
 * limits are set generously above human interactive pace but low enough to blunt
 * a scraper hammering a single IP.
 */
@RestController
@RequestMapping("/api/v1/chestshop")
public class ChestShopController {

    private static final Logger log = LoggerFactory.getLogger(ChestShopController.class);

    private final ChestShopService chestShopService;

    public ChestShopController(ChestShopService chestShopService) {
        this.chestShopService = chestShopService;
    }

    /**
     * GET /api/v1/chestshop/shops
     * Live shop directory. All filters optional: {@code itemKey}, {@code material},
     * {@code firmId}, {@code buyable} (offers a buy price), {@code inStock}
     * (admin or stock &gt; 0), {@code search} (item name/material). Paged.
     */
    @GetMapping("/shops")
    @RateLimit(personalPerMinute = 120, businessPerMinute = 600, anonymousPerMinute = 120)
    public ResponseEntity<ChestShopShopsResponse> listShops(
            @RequestParam(required = false) String itemKey,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) Integer firmId,
            @RequestParam(defaultValue = "false") boolean buyable,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {

        ChestShopShopsResponse response = chestShopService.listShops(
                itemKey, material, firmId, buyable, inStock, search, page, limit);
        log.info("GET /chestshop/shops itemKey={} material={} firmId={} buyable={} inStock={} → {} of {} (page {}/{})",
                itemKey, material, firmId, buyable, inStock,
                response.items().size(), response.totalItems(), response.page(), response.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/chestshop/items
     * Item directory with all-time aggregate trade stats. Optional {@code search}
     * over item name / material / key. Paged. Cached.
     */
    @GetMapping("/items")
    @RateLimit(personalPerMinute = 120, businessPerMinute = 600, anonymousPerMinute = 120)
    public ResponseEntity<ChestShopItemsResponse> listItems(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {

        ChestShopItemsResponse response = chestShopService.listItems(search, page, limit);
        log.info("GET /chestshop/items search={} → {} of {} (page {}/{})",
                search, response.items().size(), response.totalItems(), response.page(), response.totalPages());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/chestshop/items/{itemKey}
     * Per-item detail: windowed stats (default 30d), cheapest live shops, and a
     * daily price/volume series. Cached per (itemKey, days).
     */
    @GetMapping("/items/{itemKey}")
    @RateLimit(personalPerMinute = 120, businessPerMinute = 600, anonymousPerMinute = 120)
    public ResponseEntity<ChestShopItemDetailResponse> getItem(
            @PathVariable String itemKey,
            @RequestParam(defaultValue = "30") int days) {

        ChestShopItemDetailResponse response = chestShopService.getItem(itemKey, days);
        log.info("GET /chestshop/items/{} days={} → trades={} activeShops={}",
                itemKey, days, response.tradeCount(), response.activeShopCount());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/chestshop/stats
     * Global market totals (sales, volume, distinct items, active shops). Cached.
     */
    @GetMapping("/stats")
    @RateLimit(personalPerMinute = 120, businessPerMinute = 600, anonymousPerMinute = 120)
    public ResponseEntity<ChestShopMarketStatsResponse> stats() {
        ChestShopMarketStatsResponse response = chestShopService.marketStats();
        log.info("GET /chestshop/stats → sales={} volume={} items={} shops={}",
                response.totalSales(), response.totalVolume(), response.distinctItems(), response.activeShops());
        return ResponseEntity.ok(response);
    }
}
