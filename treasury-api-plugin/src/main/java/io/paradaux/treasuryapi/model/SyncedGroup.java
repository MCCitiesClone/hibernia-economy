package io.paradaux.treasuryapi.model;

import lombok.Data;

/** An explorer group that mirrors a LuckPerms node (luckperms_node IS NOT NULL). */
@Data
public class SyncedGroup {
    private int groupId;
    private String luckpermsNode;
}
