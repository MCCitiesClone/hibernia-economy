package io.paradaux.chestshop.dao;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Persistence boundary for the item-code store: the integer-id ↔ serialized-item
 * blob mapping that lets a shop reference a full {@code ItemStack} by a short code
 * on its sign. Pure storage — no serialization, encoding, or business logic; those
 * live in {@link io.paradaux.chestshop.services.ItemCodeService}.
 *
 * <p>The interface is storage-agnostic on purpose: the current implementation is
 * SQLite ({@link io.paradaux.chestshop.dao.impl.SqliteItemCodeRepository}), but
 * swapping to the shared MariaDB later is a new implementation behind this same
 * contract — no caller changes.
 */
public interface ItemCodeRepository {

    /** One stored item: its auto-id and the serialized (Base64) blob. */
    record StoredItem(int id, String blob) {}

    /** The id of an existing row whose blob equals {@code blob}, if any. */
    Optional<Integer> findIdByBlob(String blob);

    /** Inserts a new blob and returns its generated id. */
    int insert(String blob);

    /** The blob stored under {@code id}, if present. */
    Optional<String> findBlobById(int id);

    /** Replaces the blob stored under {@code id} (used by the metadata re-serialiser). */
    void updateBlob(int id, String blob);

    /** Visits every stored item — used by the one-time metadata migration. */
    void forEach(Consumer<StoredItem> action);

    /** Total stored items. */
    long count();
}
