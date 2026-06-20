package io.paradaux.business.services;

import io.paradaux.business.model.FirmRole;
import io.paradaux.business.model.FirmRolePermission;

import java.util.List;
import java.util.UUID;

public interface FirmRoleService {

    void createRole(String firmName, String roleName, int rankOrder, UUID actorId);

    void deleteRole(String firmName, String roleName, UUID actorId);

    void renameRole(String firmName, String oldName, String newName, UUID actorId);

    void addRolePermission(String firmName, String roleName, String permission, UUID actorId);

    void removeRolePermission(String firmName, String roleName, String permission, UUID actorId);

    List<FirmRole> getFirmRoles(String firmName, UUID actorId);
    List<FirmRole> getFirmRoles(int firmId);

    List<FirmRolePermission> getFirmRolePermissions(String firmName, String roleName, UUID actorId);
    List<FirmRolePermission> getFirmRolePermissions(int firmId, String roleName);
}
