package io.paradaux.treasuryapi.services;

import io.paradaux.treasuryapi.model.economy.ApiKey;

import java.util.List;
import java.util.UUID;

public interface ApiKeyService {
    ApiKey issuePersonalKey(int accountId, UUID ownerUuid);
    ApiKey issueBusinessKey(int firmId, UUID ownerUuid);
    ApiKey reissueKey(int keyId);
    void revokeKey(int keyId);

    /** Looks up a single key by id, or {@code null} if none exists. */
    ApiKey getKey(int keyId);

    /** Lists the keys of the given type owned by the player, ordered by key id. */
    List<ApiKey> listKeys(UUID ownerUuid, String keyType);

    /** Lists BUSINESS keys for every firm the player is currently employed at. */
    List<ApiKey> listBusinessKeysAccessibleByEmployee(UUID employeeUuid);
}
