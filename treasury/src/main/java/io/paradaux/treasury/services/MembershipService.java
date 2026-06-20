package io.paradaux.treasury.services;

import io.paradaux.treasury.model.economy.AccountMember;

import java.util.List;
import java.util.UUID;

public interface MembershipService {

    // ── Checks (consult both direct-UUID rows and LP group rows) ──

    boolean isMember(int accountId, UUID uuid);
    boolean isAuthorizer(int accountId, UUID uuid);

    // ── Individual UUID CRUD ──

    void addMember(int accountId, UUID memberUuid, UUID addedByUuid);
    void removeMember(int accountId, UUID memberUuid);
    List<AccountMember> getMembers(int accountId);

    void addAuthorizer(int accountId, UUID authorizerUuid, UUID addedByUuid);
    void removeAuthorizer(int accountId, UUID authorizerUuid);
    List<AccountMember> getAuthorizers(int accountId);

    // ── LP Group CRUD ──

    void addGroupMember(int accountId, String lpGroup, UUID addedByUuid);
    void removeGroupMember(int accountId, String lpGroup);
    List<String> getGroupMembers(int accountId);

    void addGroupAuthorizer(int accountId, String lpGroup, UUID addedByUuid);
    void removeGroupAuthorizer(int accountId, String lpGroup);
    List<String> getGroupAuthorizers(int accountId);
}
