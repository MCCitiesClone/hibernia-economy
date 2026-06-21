package io.paradaux.treasury.services.impl;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.mappers.GroupMembershipMapper;
import io.paradaux.treasury.mappers.MembershipMapper;
import io.paradaux.treasury.model.economy.AccountMember;
import io.paradaux.treasury.services.MembershipService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.jetbrains.annotations.Nullable;
import org.mybatis.guice.transactional.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class MembershipServiceImpl implements MembershipService {

    private final MembershipMapper membershipMapper;
    private final GroupMembershipMapper groupMembershipMapper;

    @Nullable
    private LuckPerms luckPerms;

    @Inject
    public MembershipServiceImpl(MembershipMapper membershipMapper,
                                 GroupMembershipMapper groupMembershipMapper) {
        this.membershipMapper = membershipMapper;
        this.groupMembershipMapper = groupMembershipMapper;
    }

    @Inject(optional = true)
    public void setLuckPerms(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
        log.info("LuckPerms integration active — group-based membership checks enabled.");
    }

    // ── Checks ──

    @Override
    @Transactional
    public boolean isMember(int accountId, UUID uuid) {
        if (membershipMapper.isMember(accountId, uuid) > 0) return true;
        Set<String> groups = getGroupsForUuid(uuid);
        if (groups.isEmpty()) return false;
        return groupMembershipMapper.isAnyGroupMember(accountId, groups) > 0;
    }

    @Override
    @Transactional
    public boolean isAuthorizer(int accountId, UUID uuid) {
        if (membershipMapper.isAuthorizer(accountId, uuid) > 0) return true;
        Set<String> groups = getGroupsForUuid(uuid);
        if (groups.isEmpty()) return false;
        return groupMembershipMapper.isAnyGroupAuthorizer(accountId, groups) > 0;
    }

    @Override
    @Transactional
    public boolean isViewer(int accountId, UUID uuid) {
        if (membershipMapper.isViewer(accountId, uuid) > 0) return true;
        Set<String> groups = getGroupsForUuid(uuid);
        if (groups.isEmpty()) return false;
        return groupMembershipMapper.isAnyGroupViewer(accountId, groups) > 0;
    }

    @Override
    @Transactional
    public boolean canView(int accountId, UUID uuid) {
        // Members (and authorizers, who are members) can already read; viewers add
        // a read-only tier on top. No spend/management rights are implied.
        return isMember(accountId, uuid) || isViewer(accountId, uuid);
    }

    // ── Individual UUID CRUD ──

    @Override
    @Transactional
    public void addMember(int accountId, UUID memberUuid, UUID addedByUuid) {
        membershipMapper.addMember(accountId, memberUuid, addedByUuid);
    }

    @Override
    @Transactional
    public void removeMember(int accountId, UUID memberUuid) {
        // Removing a member cascades to remove their authorizer row (FK constraint)
        membershipMapper.removeAuthorizer(accountId, memberUuid);
        membershipMapper.removeMember(accountId, memberUuid);
    }

    @Override
    @Transactional
    public List<AccountMember> getMembers(int accountId) {
        return membershipMapper.getMembers(accountId);
    }

    @Override
    @Transactional
    public void addAuthorizer(int accountId, UUID authorizerUuid, UUID addedByUuid) {
        // Schema FK requires authorizer to also be a member
        if (membershipMapper.isMember(accountId, authorizerUuid) == 0) {
            throw new IllegalStateException("UUID must be a member before being made an authorizer");
        }
        membershipMapper.addAuthorizer(accountId, authorizerUuid, addedByUuid);
    }

    @Override
    @Transactional
    public void removeAuthorizer(int accountId, UUID authorizerUuid) {
        membershipMapper.removeAuthorizer(accountId, authorizerUuid);
    }

    @Override
    @Transactional
    public List<AccountMember> getAuthorizers(int accountId) {
        return membershipMapper.getAuthorizers(accountId);
    }

    // ── LP Group CRUD ──

    @Override
    @Transactional
    public void addGroupMember(int accountId, String lpGroup, UUID addedByUuid) {
        groupMembershipMapper.addGroupMember(accountId, lpGroup, addedByUuid);
    }

    @Override
    @Transactional
    public void removeGroupMember(int accountId, String lpGroup) {
        // Remove group authorizer status first (FK dependency)
        groupMembershipMapper.removeGroupAuthorizer(accountId, lpGroup);
        groupMembershipMapper.removeGroupMember(accountId, lpGroup);
    }

    @Override
    @Transactional
    public List<String> getGroupMembers(int accountId) {
        return groupMembershipMapper.getGroupMembers(accountId);
    }

    @Override
    @Transactional
    public void addGroupAuthorizer(int accountId, String lpGroup, UUID addedByUuid) {
        // Group must already be a group member
        List<String> members = groupMembershipMapper.getGroupMembers(accountId);
        if (!members.contains(lpGroup)) {
            throw new IllegalStateException("Group '" + lpGroup + "' must be a group member before being made a group authorizer");
        }
        groupMembershipMapper.addGroupAuthorizer(accountId, lpGroup, addedByUuid);
    }

    @Override
    @Transactional
    public void removeGroupAuthorizer(int accountId, String lpGroup) {
        groupMembershipMapper.removeGroupAuthorizer(accountId, lpGroup);
    }

    @Override
    @Transactional
    public List<String> getGroupAuthorizers(int accountId) {
        return groupMembershipMapper.getGroupAuthorizers(accountId);
    }

    // ── Read-only viewer tier (PAR-237) ──
    //
    // A viewer is standalone (unlike an authorizer, which must be a member first),
    // so add/remove are direct with no cascade.

    @Override
    @Transactional
    public void addViewer(int accountId, UUID viewerUuid, UUID addedByUuid) {
        membershipMapper.addViewer(accountId, viewerUuid, addedByUuid);
    }

    @Override
    @Transactional
    public void removeViewer(int accountId, UUID viewerUuid) {
        membershipMapper.removeViewer(accountId, viewerUuid);
    }

    @Override
    @Transactional
    public List<AccountMember> getViewers(int accountId) {
        return membershipMapper.getViewers(accountId);
    }

    @Override
    @Transactional
    public void addGroupViewer(int accountId, String lpGroup, UUID addedByUuid) {
        groupMembershipMapper.addGroupViewer(accountId, lpGroup, addedByUuid);
    }

    @Override
    @Transactional
    public void removeGroupViewer(int accountId, String lpGroup) {
        groupMembershipMapper.removeGroupViewer(accountId, lpGroup);
    }

    @Override
    @Transactional
    public List<String> getGroupViewers(int accountId) {
        return groupMembershipMapper.getGroupViewers(accountId);
    }

    // ── Private helpers ──

    private Set<String> getGroupsForUuid(UUID uuid) {
        if (luckPerms == null) return Set.of();
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return Set.of();
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                   .map(Group::getName)
                   .collect(Collectors.toSet());
    }
}
