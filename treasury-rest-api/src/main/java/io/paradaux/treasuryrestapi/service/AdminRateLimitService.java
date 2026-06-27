package io.paradaux.treasuryrestapi.service;

import io.paradaux.treasuryrestapi.exception.ApiException;
import io.paradaux.treasuryrestapi.ratelimit.RateLimitOverrideService;
import io.paradaux.treasuryrestapi.security.AdminScope;
import io.paradaux.treasuryrestapi.security.VerifiedToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * SERVICE-scoped admin management of per-issuer rate-limit overrides (ADT-14).
 * The economy-explorer previously wrote {@code api_rate_limit_override} directly;
 * it now routes through here so this table has one writer with one place for
 * authz + validation.
 */
@Service
public class AdminRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(AdminRateLimitService.class);

    private final RateLimitOverrideService overrides;

    public AdminRateLimitService(RateLimitOverrideService overrides) {
        this.overrides = overrides;
    }

    public void setOverride(VerifiedToken verified, UUID ownerUuid, BigDecimal multiplier, String note) {
        AdminScope.require(verified);
        if (multiplier == null || multiplier.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BODY", "Field 'multiplier' must be > 0.");
        }
        overrides.set(ownerUuid, multiplier, note, verified.ownerUuid());
        log.info("Admin set rate-limit override for owner={} multiplier={} by keyId={}",
                ownerUuid, multiplier, verified.keyId());
    }

    public void clearOverride(VerifiedToken verified, UUID ownerUuid) {
        AdminScope.require(verified);
        overrides.clear(ownerUuid);
        log.info("Admin cleared rate-limit override for owner={} by keyId={}", ownerUuid, verified.keyId());
    }
}
