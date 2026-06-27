package io.paradaux.treasuryrestapi.service;

import io.paradaux.treasuryrestapi.dto.WebhookCreateRequest;
import io.paradaux.treasuryrestapi.mapper.WebhookSubscriptionMapper;
import io.paradaux.treasuryrestapi.model.WebhookSubscription;
import io.paradaux.treasuryrestapi.security.AdminScope;
import io.paradaux.treasuryrestapi.security.VerifiedToken;
import io.paradaux.treasuryrestapi.util.SsrfValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * SERVICE-scoped admin management of webhook subscriptions (ADT-14). The
 * economy-explorer previously wrote {@code webhook_subscription} directly (both
 * owner self-service and fleet-wide admin) — those writes now route through here,
 * so the table has one writer and the SSRF check lives on the authoritative side.
 *
 * <p>{@code ownerUuid} is the optional owner scope: when present the mutator only
 * affects a row owned by that UUID (player self-service); when null it's an admin
 * fleet operation by id. All mutators return the rows affected so the caller can
 * detect a not-found / not-owned subscription.
 */
@Service
public class AdminWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AdminWebhookService.class);

    private final WebhookSubscriptionMapper mapper;

    public AdminWebhookService(WebhookSubscriptionMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public long create(VerifiedToken verified, WebhookCreateRequest req) {
        AdminScope.require(verified);
        SsrfValidator.validate(req.targetUrl()); // https, public address, standard port
        WebhookSubscription sub = new WebhookSubscription();
        sub.setApiKeyId(null); // owner-scoped (explorer-managed), not key-scoped
        sub.setOwnerUuid(req.ownerUuid());
        sub.setKeyType(req.keyType());
        sub.setAccountId(req.accountId());
        sub.setFirmId(req.firmId());
        sub.setTargetUrl(req.targetUrl().trim());
        sub.setSecret(req.secret());
        mapper.insert(sub);
        log.info("Admin created webhook id={} for owner={} by keyId={}",
                sub.getSubscriptionId(), req.ownerUuid(), verified.keyId());
        return sub.getSubscriptionId();
    }

    public int setActive(VerifiedToken verified, long id, UUID ownerUuid, boolean active) {
        AdminScope.require(verified);
        return mapper.setActiveScoped(id, ownerUuid, active);
    }

    public int setUrl(VerifiedToken verified, long id, UUID ownerUuid, String targetUrl) {
        AdminScope.require(verified);
        SsrfValidator.validate(targetUrl);
        return mapper.setUrlScoped(id, ownerUuid, targetUrl.trim());
    }

    public int setSecret(VerifiedToken verified, long id, UUID ownerUuid, String secret) {
        AdminScope.require(verified);
        return mapper.setSecretScoped(id, ownerUuid, secret);
    }

    public int delete(VerifiedToken verified, long id, UUID ownerUuid) {
        AdminScope.require(verified);
        return mapper.deleteScoped(id, ownerUuid);
    }
}
