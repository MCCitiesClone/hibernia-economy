package io.paradaux.common;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * UUID ↔ {@code BINARY(16)} conversion — the single source of truth (ADT-22).
 * Previously copy-pasted in treasury and treasury-api-plugin (and mirrored in
 * the rest-api type handler and the TS DAL). Big-endian most-significant-bits
 * first, matching the {@code uuid_to_bin(...)} / column layout used in SQL.
 */
public final class UuidBin {

    private UuidBin() {}

    public static byte[] toBytes(UUID uuid) {
        if (uuid == null) return null;
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null) return null;
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
