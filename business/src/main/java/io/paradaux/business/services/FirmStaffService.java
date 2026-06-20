package io.paradaux.business.services;

import io.paradaux.business.model.FirmEmployee;
import io.paradaux.business.model.RolePermission;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public interface FirmStaffService {

    void fireEmployee(String firmName, UUID playerId, UUID actorId);
    String promoteEmployee(String firmName, UUID playerId, UUID actorId);
    String demoteEmployee(String firmName, UUID playerId, UUID actorId);
    void resignFromFirm(String firmName, UUID playerId);
    List<FirmEmployee> getCurrentEmployees(String firmName);
    List<Player> getOnlineEmployees(String firmName);

    /**
     * Hires a player into a firm. Requires {@code actorId} to hold
     * {@link RolePermission#ADMIN} on the firm — throws
     * {@link io.paradaux.hibernia.framework.exceptions.NoPermissionException}
     * otherwise. This is the public path used by the BusinessApi.
     *
     * <p>For the invite-acceptance flow, where the inviter's permission was
     * already validated at invite-creation time, use
     * {@link #hireEmployeeFromInvite(Integer, UUID, UUID)} instead.
     */
    void hireEmployee(Integer firmId, UUID playerId, UUID actorId);

    /**
     * Internal hire path used after an employment offer has been accepted.
     * Skips the ADMIN-permission check on {@code inviter} because the
     * invite row itself is the artifact of that permission — by the time
     * accept is called, an INVITE-permission check has already happened.
     *
     * <p>Only call this from {@code FirmRequestServiceImpl.acceptEmploymentOffer}.
     * Other callers must use {@link #hireEmployee(Integer, UUID, UUID)} so the
     * ADMIN check applies.
     */
    void hireEmployeeFromInvite(Integer firmId, UUID playerId, UUID inviter);
    boolean hasPermission(Integer firmId, UUID playerId, RolePermission permission);
    boolean isEmployedBy(Integer firmId, UUID playerId);
    String getCurrentRole(Integer firmId, UUID playerId);
    FirmEmployee getProprietorAsEmployee(String firmName);
}