package io.paradaux.chestshop.mappers;

import io.paradaux.chestshop.database.Item;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MyBatis mapper for the {@code items} table ({@code items.db}) — the auto-increment id
 * ↔ serialized-item-blob store a shop references by a short code on its sign. Annotation
 * SQL in the SQLite dialect (PAR-282).
 */
@Mapper
public interface ItemCodeMapper {

    /** The id of an existing row whose blob equals {@code blob}, or {@code null}. */
    @Select("SELECT id FROM items WHERE code = #{blob} LIMIT 1")
    Integer findIdByBlob(@Param("blob") String blob);

    /** Inserts a new blob; the generated id is set back onto {@code item}. */
    @Insert("INSERT INTO items(code) VALUES(#{base64ItemCode})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(Item item);

    /** The blob stored under {@code id}, or {@code null}. */
    @Select("SELECT code FROM items WHERE id = #{id}")
    String findBlobById(@Param("id") int id);

    /** Replaces the blob stored under {@code id} (used by the metadata re-serialiser). */
    @Update("UPDATE items SET code = #{blob} WHERE id = #{id}")
    void updateBlob(@Param("id") int id, @Param("blob") String blob);

    /** Every stored id, ascending — the metadata migration fetches/updates each blob by id. */
    @Select("SELECT id FROM items ORDER BY id")
    List<Integer> findAllIds();

    /** Total stored items. */
    @Select("SELECT COUNT(*) FROM items")
    long count();
}
