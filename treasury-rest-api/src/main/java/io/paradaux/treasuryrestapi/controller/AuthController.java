package io.paradaux.treasuryrestapi.controller;

import io.paradaux.treasuryrestapi.dto.MeResponse;
import io.paradaux.treasuryrestapi.dto.RotateResponse;
import io.paradaux.treasuryrestapi.ratelimit.RateLimit;
import io.paradaux.treasuryrestapi.security.VerifiedToken;
import io.paradaux.treasuryrestapi.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/rotate
     * Rotates the calling token. The old token is immediately invalidated.
     */
    @PostMapping("/rotate")
    @RateLimit(personalPerMinute = 5, businessPerMinute = 5)
    public ResponseEntity<RotateResponse> rotate(@AuthenticationPrincipal VerifiedToken verified) {
        log.info("POST /auth/rotate requested by keyId={} ownerUuid={}", verified.keyId(), verified.ownerUuid());

        RotateResponse response = authService.rotateToken(verified);

        log.info("Token rotated successfully for keyId={}, new expiry={}", verified.keyId(), response.expiresAt());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/auth/me
     * Returns the calling key's own identity and scope (a "who am I" endpoint),
     * straight from the authenticated token — no new auth plumbing.
     */
    @GetMapping("/me")
    @RateLimit(personalPerMinute = 30, businessPerMinute = 30)
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal VerifiedToken verified) {
        log.info("GET /auth/me requested by keyId={} keyType={}", verified.keyId(), verified.keyType());
        return ResponseEntity.ok(MeResponse.from(verified));
    }
}
