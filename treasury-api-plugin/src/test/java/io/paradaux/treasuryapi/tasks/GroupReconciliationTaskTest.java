package io.paradaux.treasuryapi.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit coverage for the node-key normalisation that drives the group-inheritance
 * expansion in {@link GroupReconciliationTask} (the LuckPerms-dependent paths need
 * a running LuckPerms and are smoke-tested in-game).
 */
class GroupReconciliationTaskTest {

    @Test
    void nodeKey_groupPrefixed_keptAsGroupKey() {
        assertEquals("group.government", GroupReconciliationTask.nodeKey("group.government"));
    }

    @Test
    void nodeKey_bareName_becomesGroupMembership() {
        assertEquals("group.government", GroupReconciliationTask.nodeKey("government"));
    }

    @Test
    void nodeKey_dottedNode_isAPermission() {
        assertEquals("treasury.group.government",
                GroupReconciliationTask.nodeKey("treasury.group.government"));
    }

    @Test
    void nodeKey_isLowerCased() {
        assertEquals("group.vip", GroupReconciliationTask.nodeKey("GROUP.VIP"));
        assertEquals("group.vip", GroupReconciliationTask.nodeKey("VIP"));
        assertEquals("some.perm.node", GroupReconciliationTask.nodeKey("Some.Perm.Node"));
    }
}
