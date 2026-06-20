package io.paradaux.business.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FirmRolePermission {
    private Integer firmId;
    private String roleName;
    private RolePermission permission;
}
