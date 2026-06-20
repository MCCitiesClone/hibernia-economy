package io.paradaux.treasuryrestapi.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSha256Test {

    @Test
    void matchesKnownVector() {
        // Well-known HMAC-SHA256("key", "The quick brown fox jumps over the lazy dog").
        byte[] body = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
        assertThat(HmacSha256.hex("key", body))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
        assertThat(HmacSha256.sign("key", body))
                .isEqualTo("sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void isDeterministicAndKeyed() {
        byte[] body = "{\"event\":\"transaction\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(HmacSha256.hex("s1", body)).isEqualTo(HmacSha256.hex("s1", body));
        assertThat(HmacSha256.hex("s1", body)).isNotEqualTo(HmacSha256.hex("s2", body));
    }

    @Test
    void constantTimeEquals() {
        assertThat(HmacSha256.constantTimeEquals("abc", "abc")).isTrue();
        assertThat(HmacSha256.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(HmacSha256.constantTimeEquals("abc", "abcd")).isFalse();
        assertThat(HmacSha256.constantTimeEquals(null, "abc")).isFalse();
    }
}
