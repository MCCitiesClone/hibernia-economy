package io.paradaux.treasuryapi.services;

import io.paradaux.treasuryapi.model.economy.ApiKey;

import java.util.UUID;

public interface ApiKeyService {
    ApiKey issuePersonalKey(int accountId, UUID ownerUuid);
    ApiKey issueBusinessKey(int firmId, UUID ownerUuid);
    ApiKey reissueKey(int keyId);
    void revokeKey(int keyId);
    String exportToken(int keyId);
}
