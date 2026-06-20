package io.paradaux.business.api;

import io.paradaux.business.model.FirmRole;
import io.paradaux.business.model.FirmRolePermission;

import java.util.List;
import java.util.UUID;

/**
 * Role and permission management operations.
 *
 * <p>Methods may throw {@link RuntimeException} subclasses for invalid operations
 * (e.g., firm not found, role not found, insufficient permissions).
 */
public interface RoleApi {

    /**
     * Gets all roles defined for a firm.
     *
     * @param firmId the firm ID
     * @return list of roles
     */
    List<FirmRole> getRoles(int firmId);

    /**
     * Gets the permissions assigned to a specific role within a firm.
     *
     * @param firmId   the firm ID
     * @param roleName the role name
     * @return list of role permissions
     */
    List<FirmRolePermission> getRolePermissions(int firmId, String roleName);

    /**
     * Creates a new role within a firm. Requires ADMIN permission.
     *
     * @param firmId    the firm ID
     * @param roleName  the new role name
     * @param rankOrder the rank order (lower = higher rank)
     * @param actorId   UUID of the player performing the action
     */
    void createRole(int firmId, String roleName, int rankOrder, UUID actorId);

    /**
     * Deletes a role from a firm. Requires ADMIN permission.
     *
     * @param firmId   the firm ID
     * @param roleName the role to delete
     * @param actorId  UUID of the player performing the action
     */
    void deleteRole(int firmId, String roleName, UUID actorId);

    /**
     * Adds a permission to a role. Requires ADMIN permission.
     *
     * @param firmId     the firm ID
     * @param roleName   the role name
     * @param permission the permission name
     * @param actorId    UUID of the player performing the action
     */
    void addPermission(int firmId, String roleName, String permission, UUID actorId);

    /**
     * Removes a permission from a role. Requires ADMIN permission.
     *
     * @param firmId     the firm ID
     * @param roleName   the role name
     * @param permission the permission name
     * @param actorId    UUID of the player performing the action
     */
    void removePermission(int firmId, String roleName, String permission, UUID actorId);
}
