package io.paradaux.treasuryapi.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jsonwebtoken.Jwts;
import io.paradaux.treasuryapi.mappers.ApiKeyMapper;
import io.paradaux.treasuryapi.model.config.ApiConfiguration;
import io.paradaux.treasuryapi.model.economy.ApiKey;
import io.paradaux.treasuryapi.services.ApiKeyService;
import org.mybatis.guice.transactional.Transactional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Singleton
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final long KEY_LIFETIME_DAYS = 180;

    private final ApiKeyMapper apiKeyMapper;
    private final ApiConfiguration apiConfig;

    @Inject
    public ApiKeyServiceImpl(ApiKeyMapper apiKeyMapper, ApiConfiguration apiConfig) {
        this.apiKeyMapper = apiKeyMapper;
        this.apiConfig = apiConfig;
    }

    @Override
    @Transactional
    public ApiKey issuePersonalKey(int accountId, UUID ownerUuid) {
        return issue("PERSONAL", accountId, null, ownerUuid);
    }

    @Override
    @Transactional
    public ApiKey issueBusinessKey(int firmId, UUID ownerUuid) {
        return issue("BUSINESS", null, firmId, ownerUuid);
    }

    private ApiKey issue(String keyType, Integer accountId, Integer firmId, UUID ownerUuid) {
        String jwtId = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(KEY_LIFETIME_DAYS, ChronoUnit.DAYS);

        ApiKey key = new ApiKey();
        key.setKeyType(keyType);
        key.setAccountId(accountId);
        key.setFirmId(firmId);
        key.setOwnerUuid(ownerUuid);
        key.setJwtId(jwtId);
        key.setIssuedAt(issuedAt);
        key.setExpiresAt(expiresAt);

        // Insert with placeholder token to obtain the generated keyId
        key.setToken("");
        apiKeyMapper.insert(key);

        // Build the real JWT now that we have the keyId
        String token = buildJwt(key.getKeyId(), jwtId, keyType, accountId, firmId, ownerUuid, issuedAt, expiresAt);
        key.setToken(token);

        // Update the row with the real token
        apiKeyMapper.reissue(key.getKeyId(), jwtId, token, issuedAt, expiresAt);

        return key;
    }

    @Override
    @Transactional
    public ApiKey reissueKey(int keyId) {
        ApiKey existing = apiKeyMapper.findById(keyId);
        if (existing == null) {
            throw new IllegalArgumentException("API key not found: " + keyId);
        }

        String newJwtId = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(KEY_LIFETIME_DAYS, ChronoUnit.DAYS);
        String token = buildJwt(keyId, newJwtId, existing.getKeyType(),
                existing.getAccountId(), existing.getFirmId(),
                existing.getOwnerUuid(), issuedAt, expiresAt);

        apiKeyMapper.reissue(keyId, newJwtId, token, issuedAt, expiresAt);

        existing.setJwtId(newJwtId);
        existing.setToken(token);
        existing.setIssuedAt(issuedAt);
        existing.setExpiresAt(expiresAt);
        existing.setRevoked(false);
        return existing;
    }

    @Override
    @Transactional
    public void revokeKey(int keyId) {
        apiKeyMapper.revoke(keyId);
    }

    @Override
    public String exportToken(int keyId) {
        ApiKey key = apiKeyMapper.findById(keyId);
        if (key == null) {
            throw new IllegalArgumentException("API key not found: " + keyId);
        }
        return uploadToBytebin(key.getToken());
    }

    @Override
    public ApiKey getKey(int keyId) {
        return apiKeyMapper.findById(keyId);
    }

    @Override
    public List<ApiKey> listKeys(UUID ownerUuid, String keyType) {
        return apiKeyMapper.findByOwnerAndType(ownerUuid, keyType);
    }

    @Override
    public List<ApiKey> listBusinessKeysAccessibleByEmployee(UUID employeeUuid) {
        return apiKeyMapper.findBusinessAccessibleByEmployee(employeeUuid);
    }

    private String uploadToBytebin(String token) {
        try {
            // Mirror Treasury's BytebinServiceImpl: gzip body, Bytebin-Max-Reads: 1
            // so the share link self-destructs after a single open. Without
            // gzip + the max-reads header the bytebin instance at
            // pastes.paradaux.io rejects the upload with a 4xx.
            byte[] compressed = gzip(token.getBytes(StandardCharsets.UTF_8));
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiConfig.getBytebinPostUrl()))
                    .header("Content-Type", "text/plain")
                    .header("Content-Encoding", "gzip")
                    .header("User-Agent", "TreasuryAPI-Plugin/1.0")
                    .header("Bytebin-Max-Reads", "1")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(compressed))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Bytebin upload failed with status " + response.statusCode());
            }

            String body = response.body();
            int keyStart = body.indexOf("\"key\"");
            if (keyStart == -1) {
                throw new IllegalStateException("Bytebin response did not contain a key");
            }
            int valueStart = body.indexOf('"', body.indexOf(':', keyStart) + 1) + 1;
            int valueEnd = body.indexOf('"', valueStart);
            return apiConfig.getBytebinBaseUrl() + body.substring(valueStart, valueEnd);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to upload token to bytebin", e);
        }
    }

    private static byte[] gzip(byte[] data) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    private String buildJwt(int keyId, String jwtId, String keyType,
                             Integer accountId, Integer firmId, UUID ownerUuid,
                             Instant issuedAt, Instant expiresAt) {
        SecretKey key = deriveKey(apiConfig.getJwtSecret());
        var builder = Jwts.builder()
                .header().add("kid", String.valueOf(keyId)).and()
                .subject(ownerUuid.toString())
                .claim("type", keyType)
                .id(jwtId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));

        if (accountId != null) builder.claim("acc", accountId);
        if (firmId != null)    builder.claim("firm", firmId);

        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    private SecretKey deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
