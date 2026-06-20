package io.paradaux.treasuryrestapi.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import java.time.Duration;

/**
 * Default {@link BucketProvider} — in-memory, Caffeine-backed.
 *
 * <p>Each key gets its own Bucket4j {@link Bucket} cached for an hour after
 * last access. A bucket's bandwidth is fixed at construction (capacity +
 * even refill across one minute); changing the configured limit at the
 * annotation site only takes effect for keys whose buckets have expired.
 *
 * <p>For multi-replica deployments where a single principal can hit any
 * pod, the in-memory cache is per-pod and the rate limit becomes
 * <em>per-pod</em>, not global. Switch to {@code LettuceBucketProvider} by
 * setting {@code rate-limit.redis-host} for a global counter.
 */
public class CaffeineBucketProvider implements BucketProvider {

    private final Cache<String, Bucket> cache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();

    @Override
    public Bucket bucketFor(String key, int capacityPerMinute) {
        return cache.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacityPerMinute)
                        .refillIntervally(capacityPerMinute, Duration.ofMinutes(1))
                        .build())
                .build());
    }
}
