package io.paradaux.common;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UuidBinTest {

    @Test
    void roundTripsAnyUuid() {
        UUID u = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertEquals(u, UuidBin.fromBytes(UuidBin.toBytes(u)));
        assertEquals(16, UuidBin.toBytes(u).length);
    }

    @Test
    void nullSafe() {
        assertNull(UuidBin.toBytes(null));
        assertNull(UuidBin.fromBytes(null));
    }
}
