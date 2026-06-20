package io.paradaux.treasuryrestapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-process Caffeine caching for the public ChestShop market reads. These
 * endpoints are expected to take heavy, repetitive read traffic, and their data
 * tolerates a few seconds of staleness — so each cache collapses a stampede of
 * identical requests into one DB query per short TTL.
 *
 * <p>Per-cache TTLs (and bounds) reflect cost vs. churn:
 * <ul>
 *   <li>{@code chestshopStats} — one full-table aggregate, a single key; longest
 *       TTL since it's the heaviest and least specific.</li>
 *   <li>{@code chestshopItems} / {@code chestshopItemDetail} — keyed by search
 *       page / item+window; bounded so an unbounded key space can't grow the
 *       heap, with a short TTL.</li>
 * </ul>
 *
 * <p>This is per-pod (like the rate limiter's Caffeine backend). Per-pod
 * staleness is fine for public market aggregates; there is no correctness
 * dependence on a shared cache.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String STATS = "chestshopStats";
    public static final String ITEMS = "chestshopItems";
    public static final String ITEM_DETAIL = "chestshopItemDetail";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        manager.registerCustomCache(STATS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(4)
                .build());

        manager.registerCustomCache(ITEMS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(500)
                .build());

        manager.registerCustomCache(ITEM_DETAIL, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(2_000)
                .build());

        // Fallback for any cache name not registered above: bounded + short TTL,
        // so an accidental @Cacheable name can never become an unbounded leak.
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(500));

        return manager;
    }
}
