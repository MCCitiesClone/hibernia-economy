package io.paradaux.treasuryrestapi.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Configuration
public class JwtConfig {

    /**
     * The placeholder shipped in the default-profile {@code application.yaml}.
     * Production must override this via {@code JWT_SECRET}; if the literal
     * placeholder reaches a running app, fail-fast at bean creation rather than
     * boot a service signing tokens with a known secret.
     */
    private static final String PLACEHOLDER_SECRET =
            "change-me-please-use-a-long-random-secret-key";

    /** Minimum acceptable secret length, in characters. 32 = ~192 bits of entropy at base64. */
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${api.jwt-secret}")
    private String jwtSecret;

    /**
     * Derives the HMAC-SHA256 signing key by SHA-256 hashing the configured secret.
     * This matches the key derivation used by the in-game plugin when issuing tokens.
     *
     * <p>Validates the configured secret before deriving the key:
     * <ul>
     *   <li>refuses to start if the secret is the bundled placeholder value, and</li>
     *   <li>refuses to start if the secret is shorter than {@value #MIN_SECRET_LENGTH}
     *       characters (defense-in-depth: SHA-256 always produces a 256-bit key,
     *       but a short input means the signing key is fundamentally low-entropy).</li>
     * </ul>
     */
    @Bean
    public SecretKey jwtSigningKey() throws NoSuchAlgorithmException {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "api.jwt-secret is not set. Configure JWT_SECRET in the active profile "
                    + "(see application-prod.yaml).");
        }
        if (PLACEHOLDER_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "api.jwt-secret is the placeholder value from application.yaml. "
                    + "Set the JWT_SECRET environment variable, or pin SPRING_PROFILES_ACTIVE=prod "
                    + "and configure JWT_SECRET there.");
        }
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "api.jwt-secret must be at least " + MIN_SECRET_LENGTH + " characters of "
                    + "high-entropy random data (currently " + jwtSecret.length() + ").");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
