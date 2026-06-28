package io.paradaux.treasuryapi.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasuryapi.model.economy.ApiKey;
import io.paradaux.treasuryapi.services.ApiKeyService;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Singleton
public class PersonalKeyHandler {

    private static final DateTimeFormatter EXP_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yy").withZone(ZoneId.systemDefault());

    private final ApiKeyService apiKeyService;
    private final TreasuryApi treasuryApi;
    private final Message message;

    @Inject
    public PersonalKeyHandler(ApiKeyService apiKeyService,
                              TreasuryApi treasuryApi,
                              Message message) {
        this.apiKeyService = apiKeyService;
        this.treasuryApi = treasuryApi;
        this.message = message;
    }

    public void doIssue(Player sender) {
        Account personal = treasuryApi.resolveOrCreatePersonal(sender.getUniqueId());
        ApiKey key = apiKeyService.issuePersonalKey(personal.getAccountId(), sender.getUniqueId());
        message.send(sender, "treasuryapi.personal.issue.success",
                "keyId", String.valueOf(key.getKeyId()),
                "accountId", String.valueOf(key.getAccountId()),
                "token", key.getToken());
    }

    public void doList(Player sender) {
        List<ApiKey> keys = apiKeyService.listKeys(sender.getUniqueId(), "PERSONAL");
        if (keys.isEmpty()) {
            message.send(sender, "treasuryapi.personal.list.empty");
            return;
        }
        message.send(sender, "treasuryapi.personal.list.header",
                "count", String.valueOf(keys.size()));
        for (ApiKey key : keys) {
            String status = key.isRevoked() ? "Revoked" : "Active";
            String expiry = key.isRevoked() ? "—" : EXP_FMT.format(key.getExpiresAt());
            message.send(sender, "treasuryapi.personal.list.entry",
                    "keyId", String.valueOf(key.getKeyId()),
                    "accountId", String.valueOf(key.getAccountId()),
                    "status", status,
                    "expiry", expiry);
        }
    }

    public void doReissue(Player sender, int keyId) {
        ApiKey key = apiKeyService.getKey(keyId);
        if (key == null || !"PERSONAL".equals(key.getKeyType())) {
            message.send(sender, "treasuryapi.personal.reissue.not-found");
            return;
        }
        if (!key.getOwnerUuid().equals(sender.getUniqueId())) {
            message.send(sender, "treasuryapi.personal.reissue.no-access");
            return;
        }
        // Revocation is terminal (ADT-110); the service rejects reissue of a
        // revoked key. Pre-check so the owner gets a clear message rather than an
        // error from the thrown IllegalStateException.
        if (key.isRevoked()) {
            message.send(sender, "treasuryapi.personal.reissue.revoked");
            return;
        }
        ApiKey updated = apiKeyService.reissueKey(keyId);
        message.send(sender, "treasuryapi.personal.reissue.success",
                "keyId", String.valueOf(updated.getKeyId()),
                "token", updated.getToken());
    }

    public void doRevoke(Player sender, int keyId) {
        ApiKey key = apiKeyService.getKey(keyId);
        if (key == null || !"PERSONAL".equals(key.getKeyType())) {
            message.send(sender, "treasuryapi.personal.revoke.not-found");
            return;
        }
        if (!key.getOwnerUuid().equals(sender.getUniqueId())) {
            message.send(sender, "treasuryapi.personal.revoke.no-access");
            return;
        }
        apiKeyService.revokeKey(keyId);
        message.send(sender, "treasuryapi.personal.revoke.success",
                "keyId", String.valueOf(keyId));
    }
}
