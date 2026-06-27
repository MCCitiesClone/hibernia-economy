package io.paradaux.treasuryapi.services.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.paradaux.treasuryapi.mappers.ApiKeyMapper;
import io.paradaux.treasuryapi.model.config.ApiConfiguration;
import io.paradaux.treasuryapi.model.economy.ApiKey;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the security-critical JWT mint + key-derivation path (ADT-50): a minted
 * token must verify under the key derived from the configured secret and carry
 * the correct claims. A regression in signing or derivation would otherwise ship
 * silently — these keys are the credential for the whole REST surface.
 */
class ApiKeyServiceImplTest {

    private static final String SECRET = "test-secret-please-use-32-plus-characters-xx";

    private final ApiConfiguration config = new ApiConfiguration() {
        @Override public String getJwtSecret() { return SECRET; }
    };

    /** In-memory ApiKeyMapper: insert assigns a key id the way the DB would. */
    private static ApiKeyMapper mapper(int generatedId) {
        return new ApiKeyMapper() {
            @Override public void insert(ApiKey k) { k.setKeyId(generatedId); }
            @Override public ApiKey findById(int keyId) { return null; }
            @Override public int reissue(int keyId, String jwtId, Instant issuedAt, Instant expiresAt) { return 1; }
            @Override public int revoke(int keyId) { return 0; }
            @Override public List<ApiKey> findByOwnerAndType(UUID ownerUuid, String keyType) { return List.of(); }
            @Override public List<ApiKey> findBusinessAccessibleByEmployee(UUID playerUuid) { return List.of(); }
        };
    }

    @Test
    void issuePersonalKey_mintsTokenVerifiableWithDerivedKey_carryingClaims() {
        UUID owner = UUID.randomUUID();
        ApiKey key = new ApiKeyServiceImpl(mapper(42), config).issuePersonalKey(7, owner);

        assertNotNull(key.getToken(), "mint must return the one-time token");
        assertNotNull(key.getJwtId());
        assertEquals(42, key.getKeyId());

        Jws<Claims> jws = Jwts.parser().verifyWith(deriveKey(SECRET)).build()
                .parseSignedClaims(key.getToken());
        Claims claims = jws.getPayload();
        assertEquals("PERSONAL", claims.get("type", String.class));
        assertEquals(7, claims.get("acc", Integer.class));
        assertNull(claims.get("firm"), "personal key must not carry a firm claim");
        assertEquals(owner.toString(), claims.getSubject());
        assertEquals(key.getJwtId(), claims.getId(), "jti claim must match the stored jwt_id");
        assertEquals("42", jws.getHeader().get("kid"), "kid header must be the key id");
    }

    @Test
    void issueBusinessKey_carriesFirmClaim_notAccount() {
        ApiKey key = new ApiKeyServiceImpl(mapper(9), config).issueBusinessKey(3, UUID.randomUUID());

        Claims claims = Jwts.parser().verifyWith(deriveKey(SECRET)).build()
                .parseSignedClaims(key.getToken()).getPayload();
        assertEquals("BUSINESS", claims.get("type", String.class));
        assertEquals(3, claims.get("firm", Integer.class));
        assertNull(claims.get("acc"), "business key must not carry an account claim");
    }

    @Test
    void mintedTokenFailsVerificationUnderADifferentSecret() {
        ApiKey key = new ApiKeyServiceImpl(mapper(1), config).issuePersonalKey(1, UUID.randomUUID());
        assertThrows(Exception.class, () ->
                Jwts.parser().verifyWith(deriveKey("a-totally-different-secret-32-plus-chars"))
                        .build().parseSignedClaims(key.getToken()));
    }

    /** Mirrors ApiKeyServiceImpl.deriveKey — SHA-256(secret) as the HMAC key. */
    private static SecretKey deriveKey(String secret) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(bytes, "HmacSHA256");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
