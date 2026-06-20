package io.paradaux.business.api;

import io.paradaux.business.model.FirmEmployee;
import io.paradaux.business.model.RolePermission;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Employment and permission operations.
 *
 * <p>Methods may throw {@link RuntimeException} subclasses for invalid operations
 * (e.g., player not employed, firm not found, insufficient permissions).
 */
public interface StaffApi {

    /**
     * Gets all current employees of a firm.
     *
     * @param firmId the firm ID
     * @return list of employees
     */
    List<FirmEmployee> getEmployees(int firmId);

    /**
     * Gets all currently online employees of a firm.
     *
     * @param firmId the firm ID
     * @return list of online players who are employees
     */
    List<Player> getOnlineEmployees(int firmId);

    /**
     * Checks whether a player is employed by a firm.
     *
     * @param firmId   the firm ID
     * @param playerId the player's UUID
     * @return {@code true} if the player is employed
     */
    boolean isEmployed(int firmId, UUID playerId);

    /**
     * Gets the role name of a player within a firm.
     *
     * @param firmId   the firm ID
     * @param playerId the player's UUID
     * @return the role name
     */
    String getRole(int firmId, UUID playerId);

    /**
     * Checks whether a player has a specific permission within a firm.
     *
     * @param firmId     the firm ID
     * @param playerId   the player's UUID
     * @param permission the permission to check
     * @return {@code true} if the player has the permission
     */
    boolean hasPermission(int firmId, UUID playerId, RolePermission permission);

    /**
     * Convenience check: resolves the firm that owns the given Treasury account and
     * checks whether the player holds the specified permission within it.
     *
     * <p>Returns {@code false} (not an exception) when the account is not associated
     * with any firm — callers do not need to distinguish between "not a firm account"
     * and "player lacks the permission".
     *
     * <p>Typical use:
     * <pre>{@code
     * boolean allowed = api.staff().hasPermissionForAccount(
     *         account.getAccountId(),
     *         player.getUniqueId(),
     *         RolePermission.CHESTSHOP);
     * }</pre>
     *
     * @param accountId  the Treasury account ID
     * @param playerId   the player's UUID
     * @param permission the permission to check
     * @return {@code true} if the account belongs to a firm and the player has the permission
     */
    boolean hasPermissionForAccount(int accountId, UUID playerId, RolePermission permission);

    /**
     * Hires a player into a firm.
     *
     * @param firmId   the firm ID
     * @param playerId the player's UUID
     * @param actorId  UUID of the player performing the action
     */
    void hire(int firmId, UUID playerId, UUID actorId);

    /**
     * Fires a player from a firm.
     *
     * @param firmId   the firm ID
     * @param playerId the player's UUID
     * @param actorId  UUID of the player performing the action
     */
    void fire(int firmId, UUID playerId, UUID actorId);

    /**
     * Promotes an employee within a firm.
     *
     * @param firmId   the firm ID
     * @param playerId the player's UUID
     * @param actorId  UUID of the player performing the action
     * @return the new role name after promotion
     */
    String promote(int firmId, UUID playerId, UUID actorId);

    /**
     * Demotes an employee within a firm.
     *
     * @param firmId   the firm ID
     * @param playerId the player's UUID
     * @param actorId  UUID of the player performing the action
     * @return the new role name after demotion
     */
    String demote(int firmId, UUID playerId, UUID actorId);

    /**
     * Resigns a player from a firm.
     *
     * @param firmId   the firm ID
     * @param playerId the player's UUID
     */
    void resign(int firmId, UUID playerId);
}
