package io.paradaux.business.utils;

import io.paradaux.business.model.FirmRole;
import io.paradaux.business.model.FirmRolePermission;
import io.paradaux.business.model.RolePermission;

import java.util.List;

public class RoleUtils {

    public static List<FirmRole> getDefaultRoles(Integer firmId) {
        return List.of(
                new FirmRole(firmId, "Proprietor", 1),
                new FirmRole(firmId, "Co-Proprietor", 2),
                new FirmRole(firmId, "Manager", 3),
                new FirmRole(firmId, "Supervisor", 4),
                new FirmRole(firmId, "Employee", 5)
        );
    }

    public static List<FirmRolePermission> getDefaultPermissions(Integer firmId) {
        return List.of(
                new FirmRolePermission(firmId, "Proprietor", RolePermission.ADMIN),
                new FirmRolePermission(firmId, "Co-Proprietor", RolePermission.ADMIN),
                new FirmRolePermission(firmId, "Manager", RolePermission.FINANCIAL),
                new FirmRolePermission(firmId, "Supervisor", RolePermission.CHESTSHOP),
                new FirmRolePermission(firmId, "Employee", RolePermission.DEFAULT)
        );
    }
}
