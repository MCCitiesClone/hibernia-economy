package io.paradaux.treasuryrestapi.dto;

import java.time.Instant;

public record RotateResponse(long keyId, String token, Instant issuedAt, Instant expiresAt) {}
