package io.paradaux.treasuryrestapi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirmRole {
    private int roleId;
    private String name;
    private int rankOrder;
    private boolean proprietorLike;
    private boolean defaultRole;
}
