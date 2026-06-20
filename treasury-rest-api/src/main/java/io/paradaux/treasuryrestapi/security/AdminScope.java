package io.paradaux.treasuryrestapi.security;

import io.paradaux.treasuryrestapi.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Gate for the SERVICE-scoped admin endpoints (PAR-210/PAR-217). */
public final class AdminScope {

    private AdminScope() {}

    /** Throws 403 unless the verified token is a SERVICE (admin) key. */
    public static void require(VerifiedToken verified) {
        if (verified == null || !"SERVICE".equals(verified.keyType())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This endpoint requires a SERVICE API key.");
        }
    }
}
