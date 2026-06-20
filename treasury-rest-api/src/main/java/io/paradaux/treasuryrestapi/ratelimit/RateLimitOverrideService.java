package io.paradaux.treasuryrestapi.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.paradaux.treasuryrestapi.mapper.ApiRateLimitOverrideMapper;
import io.paradaux.treasuryrestapi.model.RateLimitOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Resolves the per-issuer rate-limit multiplier with a short-lived cache so the
 * hot interceptor path doesn't hit the DB on every request. Absent override =
 * 1.00 (base limits). Admin writes invalidate the cached entry so a raised
 * limit takes effect within the cache window (and immediately for that issuer).
 */
@Service
public class RateLimitOverrideService {

    static final BigDecimal DEFAULT_MULTIPLIER = BigDecimal.ONE;
    private static final Logger log = LoggerFactory.getLogger(RateLimitOverrideService.class);

    private final ApiRateLimitOverrideMapper mapper;
    private final LoadingCache<UUID, BigDecimal> cache;

    public RateLimitOverrideService(ApiRateLimitOverrideMapper mapper) {
        this.mapper = mapper;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .build(owner -> {
                    try {
                        BigDecimal m = mapper.findMultiplier(owner);
                        return m != null ? m : DEFAULT_MULTIPLIER;
                    } catch (RuntimeException e) {
                        // Fail safe: a missing table (pre-migration) or DB blip must
                        // not break the rate-limit hot path — fall back to default.
                        log.warn("rate-limit override lookup failed, using default: {}", e.toString());
                        return DEFAULT_MULTIPLIER;
                    }
                });
    }

    /** Multiplier for an issuer; never null, defaults to 1.00. */
    public BigDecimal getMultiplier(UUID owner) {
        if (owner == null) return DEFAULT_MULTIPLIER;
        return cache.get(owner);
    }

    public List<RateLimitOverride> listAll() {
        try {
            return mapper.findAll();
        } catch (RuntimeException e) {
            log.warn("rate-limit override list failed (table missing?), returning empty: {}", e.toString());
            return List.of();
        }
    }

    public void set(UUID owner, BigDecimal multiplier, String note, UUID updatedBy) {
        mapper.upsert(owner, multiplier, note, updatedBy);
        cache.invalidate(owner);
    }

    public void clear(UUID owner) {
        mapper.delete(owner);
        cache.invalidate(owner);
    }
}
