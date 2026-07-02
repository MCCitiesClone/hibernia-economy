package io.paradaux.chestshop.services.impl;

import io.paradaux.chestshop.utils.encoding.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Codec coverage for the PAR-290 / ADT-136 item-code blob migration: new blobs are
 * plain Base64 of UTF-8 bytes, and {@link ItemCodeServiceImpl#decodeBlob} must read both
 * that and the legacy Java-serialized-String blobs (produced by the vendored
 * {@link Base64#encodeObject}) so the one-time migration can convert old rows without
 * losing any. The live re-write over {@code items.db} still needs a server smoke-test;
 * this pins the decoder logic the migration relies on.
 */
class ItemCodeBlobCodecTest {

    private static final String YAML =
            "==: org.bukkit.inventory.ItemStack\nv: 4189\ntype: STONE\n";

    @Test
    void plainEncoding_roundTrips() throws Exception {
        String blob = ItemCodeServiceImpl.encodeBlob(YAML);
        assertThat(ItemCodeServiceImpl.decodeBlob(blob)).isEqualTo(YAML);
    }

    @Test
    void decode_readsLegacyJavaSerializedBlob() throws Exception {
        // What the old code wrote: a Java-serialized String via the vendored Base64.
        String legacyBlob = Base64.encodeObject(YAML);
        assertThat(ItemCodeServiceImpl.decodeBlob(legacyBlob)).isEqualTo(YAML);
    }

    @Test
    void plainAndLegacyEncodingsDiffer_soMigrationActuallyRewrites() throws Exception {
        String plain = ItemCodeServiceImpl.encodeBlob(YAML);
        String legacy = Base64.encodeObject(YAML);
        assertThat(plain).isNotEqualTo(legacy);
        // And both decode back to the same YAML — the migration is loss-free.
        assertThat(ItemCodeServiceImpl.decodeBlob(plain)).isEqualTo(ItemCodeServiceImpl.decodeBlob(legacy));
    }

    @Test
    void plainEncoding_isNotNativeDeserialization() {
        // A plain blob's bytes are the UTF-8 YAML itself — no ObjectOutputStream header.
        byte[] raw = java.util.Base64.getDecoder().decode(ItemCodeServiceImpl.encodeBlob(YAML));
        boolean javaSerialHeader = raw.length >= 2 && (raw[0] & 0xFF) == 0xAC && (raw[1] & 0xFF) == 0xED;
        assertThat(javaSerialHeader).isFalse();
    }
}
