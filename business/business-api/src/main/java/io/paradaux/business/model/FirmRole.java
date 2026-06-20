package io.paradaux.business.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FirmRole {
    private Integer firmId;
    private String roleName;
    private Integer roleRankOrder;
}
