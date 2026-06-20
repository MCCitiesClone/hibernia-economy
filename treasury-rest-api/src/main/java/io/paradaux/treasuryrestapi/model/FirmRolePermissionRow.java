package io.paradaux.treasuryrestapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Flat row returned by the role-permissions query; grouped into lists by the service. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirmRolePermissionRow {
    private int roleId;
    private String permission;
}
