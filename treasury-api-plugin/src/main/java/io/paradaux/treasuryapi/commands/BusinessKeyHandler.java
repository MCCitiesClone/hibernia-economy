package io.paradaux.treasuryapi.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.business.api.BusinessApi;
import io.paradaux.business.model.Firm;
import io.paradaux.treasuryapi.mappers.ApiKeyMapper;
import io.paradaux.treasuryapi.model.economy.ApiKey;
import io.paradaux.treasuryapi.services.ApiKeyService;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Singleton
public class BusinessKeyHandler {

    private static final DateTimeFormatter EXP_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yy").withZone(ZoneId.systemDefault());

    private final ApiKeyService apiKeyService;
    private final ApiKeyMapper apiKeyMapper;
    private final BusinessApi businessApi;
    private final Message message;

    @Inject
    public BusinessKeyHandler(ApiKeyService apiKeyService,
                              ApiKeyMapper apiKeyMapper,
                              BusinessApi businessApi,
                              Message message) {
        this.apiKeyService = apiKeyService;
        this.apiKeyMapper = apiKeyMapper;
        this.businessApi = businessApi;
        this.message = message;
    }

    public void doIssue(Player sender, String firmName) {
        Firm firm = businessApi.firms().getFirm(firmName);
        if (firm == null) {
            message.send(sender, "treasuryapi.business.issue.firm-not-found");
            return;
        }

        if (!businessApi.firms().isProprietor(firm.getFirmId(), sender.getUniqueId())) {
            message.send(sender, "treasuryapi.business.issue.no-access");
            return;
        }

        ApiKey key = apiKeyService.issueBusinessKey(firm.getFirmId(), sender.getUniqueId());
        message.send(sender, "treasuryapi.business.issue.success",
                "keyId", String.valueOf(key.getKeyId()),
                "firmId", String.valueOf(key.getFirmId()),
                "firmName", firm.getDisplayName(),
                "token", key.getToken());
    }

    public void doList(Player sender) {
        List<ApiKey> keys = apiKeyMapper.findByOwnerAndType(sender.getUniqueId(), "BUSINESS");
        if (keys.isEmpty()) {
            message.send(sender, "treasuryapi.business.list.empty");
            return;
        }
        message.send(sender, "treasuryapi.business.list.header",
                "count", String.valueOf(keys.size()));
        for (ApiKey key : keys) {
            String status = key.isRevoked() ? "Revoked" : "Active";
            String expiry = key.isRevoked() ? "—" : EXP_FMT.format(key.getExpiresAt());
            message.send(sender, "treasuryapi.business.list.entry",
                    "keyId", String.valueOf(key.getKeyId()),
                    "firmId", String.valueOf(key.getFirmId()),
                    "status", status,
                    "expiry", expiry);
        }
    }

    public void doListAccess(Player sender) {
        List<ApiKey> keys = apiKeyMapper.findBusinessAccessibleByEmployee(sender.getUniqueId());
        if (keys.isEmpty()) {
            message.send(sender, "treasuryapi.business.list.access.empty");
            return;
        }
        message.send(sender, "treasuryapi.business.list.access.header",
                "count", String.valueOf(keys.size()));
        for (ApiKey key : keys) {
            String status = key.isRevoked() ? "Revoked" : "Active";
            String expiry = key.isRevoked() ? "—" : EXP_FMT.format(key.getExpiresAt());
            message.send(sender, "treasuryapi.business.list.access.entry",
                    "keyId", String.valueOf(key.getKeyId()),
                    "firmId", String.valueOf(key.getFirmId()),
                    "status", status,
                    "expiry", expiry);
        }
    }

    public void doExport(Player sender, int keyId) {
        ApiKey key = apiKeyMapper.findById(keyId);
        if (key == null || !"BUSINESS".equals(key.getKeyType())) {
            message.send(sender, "treasuryapi.business.export.not-found");
            return;
        }
        if (!key.getOwnerUuid().equals(sender.getUniqueId())) {
            message.send(sender, "treasuryapi.business.export.no-access");
            return;
        }
        String url = apiKeyService.exportToken(keyId);
        message.send(sender, "treasuryapi.business.export.success",
                "keyId", String.valueOf(keyId),
                "url", url);
    }

    public void doReissue(Player sender, int keyId) {
        ApiKey key = apiKeyMapper.findById(keyId);
        if (key == null || !"BUSINESS".equals(key.getKeyType())) {
            message.send(sender, "treasuryapi.business.reissue.not-found");
            return;
        }
        if (!key.getOwnerUuid().equals(sender.getUniqueId())) {
            message.send(sender, "treasuryapi.business.reissue.no-access");
            return;
        }
        ApiKey updated = apiKeyService.reissueKey(keyId);
        message.send(sender, "treasuryapi.business.reissue.success",
                "keyId", String.valueOf(updated.getKeyId()),
                "token", updated.getToken());
    }

    public void doRevoke(Player sender, int keyId) {
        ApiKey key = apiKeyMapper.findById(keyId);
        if (key == null || !"BUSINESS".equals(key.getKeyType())) {
            message.send(sender, "treasuryapi.business.revoke.not-found");
            return;
        }
        if (!key.getOwnerUuid().equals(sender.getUniqueId())) {
            message.send(sender, "treasuryapi.business.revoke.no-access");
            return;
        }
        apiKeyService.revokeKey(keyId);
        message.send(sender, "treasuryapi.business.revoke.success",
                "keyId", String.valueOf(keyId));
    }
}
