package io.paradaux.treasuryrestapi.mapper;

import io.paradaux.treasuryrestapi.model.RateLimitOverride;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Per-issuer rate-limit multiplier overrides. Absence of a row means the default (1.00). */
@Mapper
public interface ApiRateLimitOverrideMapper {

    @Select("SELECT multiplier FROM api_rate_limit_override WHERE owner_uuid_bin = #{owner}")
    BigDecimal findMultiplier(@Param("owner") UUID owner);

    @Select("SELECT owner_uuid_bin AS owner_uuid, multiplier, note, updated_at " +
            "FROM api_rate_limit_override")
    List<RateLimitOverride> findAll();

    @Insert("INSERT INTO api_rate_limit_override (owner_uuid_bin, multiplier, note, updated_by_bin) " +
            "VALUES (#{owner}, #{multiplier}, #{note}, #{updatedBy}) " +
            "ON DUPLICATE KEY UPDATE multiplier = VALUES(multiplier), note = VALUES(note), " +
            "                        updated_by_bin = VALUES(updated_by_bin)")
    int upsert(@Param("owner") UUID owner,
               @Param("multiplier") BigDecimal multiplier,
               @Param("note") String note,
               @Param("updatedBy") UUID updatedBy);

    @Delete("DELETE FROM api_rate_limit_override WHERE owner_uuid_bin = #{owner}")
    int delete(@Param("owner") UUID owner);
}
