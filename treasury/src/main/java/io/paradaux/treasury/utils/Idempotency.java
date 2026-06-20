package io.paradaux.treasury.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Idempotency {
    private Idempotency() {
    }

    public static byte[] sha256(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
