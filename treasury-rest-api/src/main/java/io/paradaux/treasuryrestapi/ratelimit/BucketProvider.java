package io.paradaux.treasuryrestapi.ratelimit;

import io.github.bucket4j.Bucket;

/**
 * Pluggable bucket source. Two implementations:
 * <ul>
 *   <li>{@code CaffeineBucketProvider} — in-memory, single-node. Default.</li>
 *   <li>{@code LettuceBucketProvider} — Redis-backed, shared across replicas.
 *       Activated when {@code rate-limit.redis-host} is configured.</li>
 * </ul>
 *
 * <p>Buckets are keyed by a string of the form {@code "{keyId}:{routeId}"} so
 * each (principal × endpoint) pair gets its own counter. Implementations are
 * expected to memoise — calling {@code bucketFor} twice with the same key
 * must return the same logical bucket (or a proxy onto the same shared
 * counter, in the Redis case).
 */
public interface BucketProvider {

    /**
     * @param key                identity for the bucket; must be unique per
     *                           (principal, route) tuple
     * @param capacityPerMinute  bucket capacity AND refill rate per minute.
     *                           A clean 60 means up to 60 in any rolling
     *                           minute, refilling evenly (~1/sec).
     */
    Bucket bucketFor(String key, int capacityPerMinute);
}
