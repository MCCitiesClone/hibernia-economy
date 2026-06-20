package io.paradaux.treasury.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyTest {

    @Test
    void sha256_returnsThirtyTwoBytes() {
        assertThat(Idempotency.sha256("anything")).hasSize(32);
        assertThat(Idempotency.sha256("")).hasSize(32);
    }

    @Test
    void sha256_isDeterministic() {
        byte[] a = Idempotency.sha256("transfer:1:2:100.00");
        byte[] b = Idempotency.sha256("transfer:1:2:100.00");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void sha256_differsForDifferentInputs() {
        assertThat(Idempotency.sha256("a")).isNotEqualTo(Idempotency.sha256("b"));
        assertThat(Idempotency.sha256("transfer:1:2:100.00"))
                .isNotEqualTo(Idempotency.sha256("transfer:1:2:100.01"));
    }

    @Test
    void sha256_handlesUnicode() {
        // UTF-8 encoded under the hood — exercising the path with non-ASCII
        byte[] hash = Idempotency.sha256("réimbursement:€100");
        assertThat(hash).hasSize(32);
    }

    @Test
    void sha256_throwsOnNullInput() {
        assertThatThrownBy(() -> Idempotency.sha256(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sha256_knownInputProducesExpectedHashLength() {
        // Spot check: SHA-256("") is well-known.
        // We don't pin the bytes (different JDKs same result), just exercise the path.
        byte[] hash = Idempotency.sha256("");
        assertThat(hash).hasSize(32);
        // First byte of SHA-256("") is 0xe3
        assertThat(hash[0]).isEqualTo((byte) 0xe3);
    }
}
