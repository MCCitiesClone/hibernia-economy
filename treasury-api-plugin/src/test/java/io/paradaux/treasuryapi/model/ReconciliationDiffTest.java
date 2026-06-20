package io.paradaux.treasuryapi.model;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationDiffTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @Test
    void addsDesiredNotCurrent_removesCurrentNotDesired() {
        ReconciliationDiff.Result d = ReconciliationDiff.of(Set.of(B, C), Set.of(A, B));
        assertEquals(Set.of(C), d.toAdd());    // desired \ current
        assertEquals(Set.of(A), d.toRemove()); // current \ desired
    }

    @Test
    void noChangeWhenEqual() {
        ReconciliationDiff.Result d = ReconciliationDiff.of(Set.of(A, B), Set.of(B, A));
        assertTrue(d.toAdd().isEmpty());
        assertTrue(d.toRemove().isEmpty());
    }

    @Test
    void emptyDesiredRemovesAll() {
        ReconciliationDiff.Result d = ReconciliationDiff.of(Set.of(), Set.of(A, B));
        assertTrue(d.toAdd().isEmpty());
        assertEquals(Set.of(A, B), d.toRemove());
    }

    @Test
    void emptyCurrentAddsAll() {
        ReconciliationDiff.Result d = ReconciliationDiff.of(Set.of(A, B), Set.of());
        assertEquals(Set.of(A, B), d.toAdd());
        assertTrue(d.toRemove().isEmpty());
    }
}
