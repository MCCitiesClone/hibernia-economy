package io.paradaux.treasuryrestapi.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.codec.ByteArrayCodec;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Redis-backed {@link BucketProvider} — counters are shared across pods.
 * Activated when {@code rate-limit.redis-host} is set; see
 * {@link RateLimitConfig}.
 *
 * <p>Uses Lettuce as the Redis client and Bucket4j's CAS-based proxy manager
 * for atomic cross-pod refills. Bucket state TTLs at one hour after last
 * access (matching the in-memory cache's behaviour) so abandoned principals
 * don't leak Redis keys.
 */
public class LettuceBucketProvider implements BucketProvider, AutoCloseable {

    private final RedisClient redisClient;
    private final ProxyManager<byte[]> proxyManager;

    public LettuceBucketProvider(String host, int port, String password) {
        RedisURI.Builder uriBuilder = RedisURI.builder().withHost(host).withPort(port);
        if (password != null && !password.isBlank()) {
            uriBuilder.withPassword(password.toCharArray());
        }
        RedisURI uri = uriBuilder.build();
        this.redisClient = RedisClient.create(uri);
        this.proxyManager = LettuceBasedProxyManager.builderFor(
                        redisClient.connect(ByteArrayCodec.INSTANCE))
                .withClientSideConfig(ClientSideConfig.getDefault()
                        .withExpirationAfterWriteStrategy(
                                io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
                                        .fixedTimeToLive(Duration.ofHours(1))))
                .build();
    }

    @Override
    public Bucket bucketFor(String key, int capacityPerMinute) {
        BucketConfiguration cfg = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacityPerMinute)
                        .refillIntervally(capacityPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
        byte[] keyBytes = ("rl:" + key).getBytes(StandardCharsets.UTF_8);
        return proxyManager.builder().build(keyBytes, () -> cfg);
    }

    @Override
    public void close() {
        redisClient.shutdown();
    }
}
