package io.paradaux.treasuryapi.mappers;

import io.paradaux.treasuryapi.model.economy.ApiKey;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiKeyMapper {

    @Insert("""
            INSERT INTO api_keys (key_type, account_id, firm_id, owner_uuid_bin, jwt_id, token, issued_at, expires_at)
            VALUES (#{keyType}, #{accountId}, #{firmId}, #{ownerUuid}, #{jwtId}, #{token}, #{issuedAt}, #{expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "keyId", keyColumn = "key_id")
    void insert(ApiKey apiKey);

    @Select("""
            SELECT key_id, key_type, account_id, firm_id, owner_uuid_bin,
                   jwt_id, token, issued_at, expires_at, revoked
              FROM api_keys
             WHERE key_id = #{keyId}
            """)
    @Results(id = "apiKeyMap", value = {
            @Result(column = "key_id",         property = "keyId",     id = true),
            @Result(column = "key_type",       property = "keyType"),
            @Result(column = "account_id",     property = "accountId"),
            @Result(column = "firm_id",        property = "firmId"),
            @Result(column = "owner_uuid_bin", property = "ownerUuid"),
            @Result(column = "jwt_id",         property = "jwtId"),
            @Result(column = "token",          property = "token"),
            @Result(column = "issued_at",      property = "issuedAt",  javaType = Instant.class),
            @Result(column = "expires_at",     property = "expiresAt", javaType = Instant.class),
            @Result(column = "revoked",        property = "revoked")
    })
    ApiKey findById(@Param("keyId") int keyId);

    @Update("""
            UPDATE api_keys
               SET jwt_id     = #{jwtId},
                   token      = #{token},
                   issued_at  = #{issuedAt},
                   expires_at = #{expiresAt},
                   revoked    = 0
             WHERE key_id = #{keyId}
            """)
    int reissue(@Param("keyId") int keyId,
                @Param("jwtId") String jwtId,
                @Param("token") String token,
                @Param("issuedAt") Instant issuedAt,
                @Param("expiresAt") Instant expiresAt);

    @Update("UPDATE api_keys SET revoked = 1 WHERE key_id = #{keyId}")
    int revoke(@Param("keyId") int keyId);

    // ---- Typed list queries (owned by the caller) ----

    @Select("""
            SELECT key_id, key_type, account_id, firm_id, owner_uuid_bin,
                   jwt_id, token, issued_at, expires_at, revoked
              FROM api_keys
             WHERE key_type = #{keyType}
               AND owner_uuid_bin = #{ownerUuid}
             ORDER BY key_id
            """)
    @ResultMap("apiKeyMap")
    List<ApiKey> findByOwnerAndType(@Param("ownerUuid") UUID ownerUuid,
                                    @Param("keyType") String keyType);

    // ---- Business access — keys for firms the player is employed at ----

    @Select("""
            SELECT DISTINCT ak.key_id, ak.key_type, ak.account_id, ak.firm_id, ak.owner_uuid_bin,
                   ak.jwt_id, ak.token, ak.issued_at, ak.expires_at, ak.revoked
              FROM api_keys ak
              JOIN firm_employee fe ON ak.firm_id = fe.firm_id
             WHERE ak.key_type = 'BUSINESS'
               AND fe.player_uuid_bin = #{playerUuid}
               AND fe.is_current = 1
             ORDER BY ak.key_id
            """)
    @ResultMap("apiKeyMap")
    List<ApiKey> findBusinessAccessibleByEmployee(@Param("playerUuid") UUID playerUuid);
}
