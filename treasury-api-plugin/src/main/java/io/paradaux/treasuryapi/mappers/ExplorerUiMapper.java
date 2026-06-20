package io.paradaux.treasuryapi.mappers;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * Writes the explorer auth tables from in-game commands: role grants, the
 * identity link, and link-code redemption. UUID params use the registered
 * {@code UuidBinaryTypeHandler}.
 */
@Mapper
public interface ExplorerUiMapper {

    @Insert("INSERT IGNORE INTO explorer_role (player_uuid_bin, role, granted_by_uuid_bin) " +
            "VALUES (#{uuid}, #{role}, #{grantedBy})")
    void addRole(@Param("uuid") UUID uuid, @Param("role") String role, @Param("grantedBy") UUID grantedBy);

    @Delete("DELETE FROM explorer_role WHERE player_uuid_bin = #{uuid} AND role = #{role}")
    int removeRole(@Param("uuid") UUID uuid, @Param("role") String role);

    @Select("SELECT role FROM explorer_role WHERE player_uuid_bin = #{uuid} ORDER BY role")
    List<String> listRoles(@Param("uuid") UUID uuid);

    /** Returns the keycloak_sub for a valid (unexpired) code, or null. */
    @Select("SELECT keycloak_sub FROM explorer_link_code WHERE code = #{code} AND expires_at > NOW()")
    String findValidLinkSub(@Param("code") String code);

    @Delete("DELETE FROM explorer_link_code WHERE code = #{code}")
    void deleteLinkCode(@Param("code") String code);

    @Insert("INSERT INTO explorer_identity (keycloak_sub, player_uuid_bin, minecraft_name, linked_by) " +
            "VALUES (#{sub}, #{uuid}, #{name}, #{linkedBy}) " +
            "ON DUPLICATE KEY UPDATE player_uuid_bin = VALUES(player_uuid_bin), minecraft_name = VALUES(minecraft_name)")
    void upsertIdentity(@Param("sub") String sub, @Param("uuid") UUID uuid,
                        @Param("name") String name, @Param("linkedBy") String linkedBy);
}
