package io.paradaux.treasuryrestapi.ratelimit;

import tools.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import io.paradaux.treasuryrestapi.dto.ErrorResponse;
import io.paradaux.treasuryrestapi.security.VerifiedToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Per-ISSUER rate limiting. Reads {@link RateLimit} off the matched controller
 * method, picks the base limit for the principal's key type, scales it by the
 * issuer's override multiplier, and consumes one token from a Bucket4j bucket
 * keyed by the issuer (the human who created the key — {@code ownerUuid}), not
 * the individual key. All of a person's keys therefore share one budget per
 * endpoint.
 *
 * <p>The effective limit is part of the bucket key, so an admin raising an
 * issuer's multiplier takes effect immediately (a fresh bucket at the new
 * capacity) rather than waiting for the old bucket's TTL.
 *
 * <p>If {@link RateLimit#anonymousPerMinute()} is {@code > 0} on the endpoint
 * and the request arrives without a verified principal, the bucket is keyed by
 * the client IP instead. This caps scraping on intentionally-public endpoints
 * without breaking legitimate anonymous use.
 *
 * <p>Every throttled and allowed request is counted into Micrometer
 * ({@code treasury_api_key_requests_total}) for the admin dashboard / Grafana.
 *
 * <p>Fails OPEN: if the rate-limit backend (Redis) errors, the request is
 * allowed rather than 500'd, so a Redis blip can't take the API down.
 *
 * <p>Endpoints without {@code @RateLimit}, or anonymous traffic on an endpoint
 * whose {@code anonymousPerMinute} is left at the default {@code 0}, are not
 * throttled.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final String METRIC = "treasury_api_key_requests_total";

    private final BucketProvider bucketProvider;
    private final ObjectMapper objectMapper;
    private final RateLimitOverrideService overrides;
    private final MeterRegistry metrics;

    public RateLimitInterceptor(BucketProvider bucketProvider, ObjectMapper objectMapper,
                                RateLimitOverrideService overrides, MeterRegistry metrics) {
        this.bucketProvider = bucketProvider;
        this.objectMapper = objectMapper;
        this.overrides = overrides;
        this.metrics = metrics;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) return true;
        RateLimit ann = hm.getMethodAnnotation(RateLimit.class);
        if (ann == null) return true;

        String routeKey = hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        VerifiedToken token = (auth != null && auth.getPrincipal() instanceof VerifiedToken vt) ? vt : null;

        if (token == null) {
            if (ann.anonymousPerMinute() <= 0) return true;
            return throttleAnonymous(request, response, ann, routeKey);
        }
        return throttleAuthenticated(response, ann, routeKey, token);
    }

    private boolean throttleAuthenticated(HttpServletResponse response, RateLimit ann,
                                          String routeKey, VerifiedToken token) throws Exception {
        UUID issuer = token.ownerUuid();
        String issuerKey = issuer != null ? issuer.toString() : ("key:" + token.keyId());

        int base = "BUSINESS".equalsIgnoreCase(token.keyType())
                ? ann.businessPerMinute()
                : ann.personalPerMinute();
        BigDecimal multiplier = overrides.getMultiplier(issuer);
        int limit = Math.max(1, BigDecimal.valueOf(base).multiply(multiplier)
                .setScale(0, RoundingMode.HALF_UP).intValue());

        // Limit is part of the key so a multiplier change re-buckets immediately.
        String bucketKey = issuerKey + ":" + routeKey + ":" + limit;
        return consume(response, routeKey, bucketKey, limit, token, issuerKey);
    }

    private boolean throttleAnonymous(HttpServletRequest request, HttpServletResponse response,
                                      RateLimit ann, String routeKey) throws Exception {
        String ip = clientIp(request);
        int limit = Math.max(1, ann.anonymousPerMinute());
        String issuerKey = "anon:" + ip;
        // Limit is part of the key for the same reason as the authenticated path.
        String bucketKey = issuerKey + ":" + routeKey + ":" + limit;
        return consume(response, routeKey, bucketKey, limit, null, issuerKey);
    }

    /**
     * Try to consume one token from the bucket and write the response side-effects
     * (headers on success, 429 + ErrorResponse on throttle). {@code token} is null
     * when throttling an anonymous request; the metric label set differs accordingly.
     */
    private boolean consume(HttpServletResponse response, String routeKey,
                            String bucketKey, int limit,
                            VerifiedToken token, String issuerKey) throws Exception {
        ConsumptionProbe probe;
        try {
            Bucket bucket = bucketProvider.bucketFor(bucketKey, limit);
            probe = bucket.tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException e) {
            // Fail open: a Redis/backend error must not take down the API.
            log.warn("Rate-limit backend error on {} — failing open (request allowed): {}",
                    routeKey, e.toString());
            count(token, issuerKey, "allowed");
            return true;
        }

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
            response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            count(token, issuerKey, "allowed");
            return true;
        }

        long retryAfterSeconds = Math.max(1L, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        log.warn("Rate limit exceeded: issuer={} route={} keyType={} limit={}/min retryAfter={}s",
                issuerKey, routeKey,
                token != null ? token.keyType() : "ANONYMOUS",
                limit, retryAfterSeconds);
        count(token, issuerKey, "throttled");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", "0");
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                "RATE_LIMIT_EXCEEDED",
                "Rate limit of " + limit + " requests/minute exceeded for this endpoint. "
                        + "Retry after " + retryAfterSeconds + " second(s)."));
        return false;
    }

    private void count(VerifiedToken token, String issuerKey, String outcome) {
        try {
            metrics.counter(METRIC,
                    "key_id", token != null ? Long.toString(token.keyId()) : "0",
                    "issuer", issuerKey,
                    "key_type", token != null
                            ? (token.keyType() != null ? token.keyType() : "unknown")
                            : "ANONYMOUS",
                    "outcome", outcome).increment();
        } catch (RuntimeException e) {
            log.debug("metric emit failed: {}", e.toString());
        }
    }

    /**
     * First hop of {@code X-Forwarded-For}, falling back to the socket remote
     * address. Spring's {@code server.forward-headers-strategy=framework} (set in
     * prod/uat) makes XFF the canonical client identity when behind the cluster
     * ingress.
     */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
