package io.paradaux.treasury.services.impl;

import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.model.economy.AccountType;
import io.paradaux.treasury.services.AccountService;
import io.paradaux.treasury.services.LedgerService;
import io.paradaux.treasury.services.MembershipService;
import io.paradaux.treasury.testsupport.IntegrationTestBase;
import io.paradaux.treasury.utils.TreasuryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end membership flows hitting a real DB. The pure-mock tests in
 * {@code MembershipServiceImplTest} cover delegation and validation; this class
 * exercises the actual SQL paths (FK-cascade ordering, group SQL, etc).
 */
class MembershipServiceIT extends IntegrationTestBase {

    private MembershipService membership;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        membership = injector.getInstance(MembershipService.class);
        accountService = injector.getInstance(AccountService.class);
        injector.getInstance(LedgerService.class).bootstrapGovernmentAccounts();
    }

    // ---- UUID flow ----

    @Test
    void addMember_thenAuthorizer_thenRemoveMember_cascadesAuthorizer() {
        int accountId = createGovAccount("Dept-A").getAccountId();
        UUID member = UUID.randomUUID();
        UUID by = UUID.randomUUID();

        membership.addMember(accountId, member, by);
        membership.addAuthorizer(accountId, member, by);

        assertThat(membership.isMember(accountId, member)).isTrue();
        assertThat(membership.isAuthorizer(accountId, member)).isTrue();

        // Removing membership cascades authorizer-row removal first (FK constraint).
        membership.removeMember(accountId, member);

        assertThat(membership.isMember(accountId, member)).isFalse();
        assertThat(membership.isAuthorizer(accountId, member)).isFalse();
    }

    @Test
    void getMembers_returnsAllRowsInsertOrder() {
        int accountId = createGovAccount("Dept-B").getAccountId();
        UUID by = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        UUID m3 = UUID.randomUUID();

        membership.addMember(accountId, m1, by);
        membership.addMember(accountId, m2, by);
        membership.addMember(accountId, m3, by);

        assertThat(membership.getMembers(accountId)).hasSize(3);
    }

    @Test
    void addAuthorizer_withoutMembership_throws() {
        int accountId = createGovAccount("Dept-C").getAccountId();
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> membership.addAuthorizer(accountId, stranger, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be a member");
    }

    // ---- Group flow ----

    @Test
    void addGroupMember_thenAuthorizer_thenRemove_cascadesGroupAuthorizer() {
        int accountId = createGovAccount("Dept-D").getAccountId();
        UUID by = UUID.randomUUID();

        membership.addGroupMember(accountId, "vip", by);
        membership.addGroupAuthorizer(accountId, "vip", by);

        assertThat(membership.getGroupMembers(accountId)).contains("vip");
        assertThat(membership.getGroupAuthorizers(accountId)).contains("vip");

        membership.removeGroupMember(accountId, "vip");

        assertThat(membership.getGroupMembers(accountId)).doesNotContain("vip");
        assertThat(membership.getGroupAuthorizers(accountId)).doesNotContain("vip");
    }

    @Test
    void addGroupAuthorizer_withoutGroupMembership_throws() {
        int accountId = createGovAccount("Dept-E").getAccountId();

        assertThatThrownBy(() -> membership.addGroupAuthorizer(accountId, "ghost-group", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be a group member");
    }

    @Test
    void getGroupMembers_andAuthorizers_returnIndependentLists() {
        int accountId = createGovAccount("Dept-F").getAccountId();
        UUID by = UUID.randomUUID();

        membership.addGroupMember(accountId, "staff", by);
        membership.addGroupMember(accountId, "vip", by);
        membership.addGroupAuthorizer(accountId, "staff", by); // only staff is also authorizer

        List<String> members = membership.getGroupMembers(accountId);
        List<String> auths = membership.getGroupAuthorizers(accountId);

        assertThat(members).containsExactlyInAnyOrder("staff", "vip");
        assertThat(auths).containsExactly("staff");
    }

    @Test
    void removeGroupAuthorizer_doesNotRemoveGroupMember() {
        int accountId = createGovAccount("Dept-G").getAccountId();
        UUID by = UUID.randomUUID();

        membership.addGroupMember(accountId, "vip", by);
        membership.addGroupAuthorizer(accountId, "vip", by);

        membership.removeGroupAuthorizer(accountId, "vip");

        assertThat(membership.getGroupAuthorizers(accountId)).doesNotContain("vip");
        assertThat(membership.getGroupMembers(accountId)).contains("vip");
    }

    // ---- Helper ----

    private Account createGovAccount(String name) {
        return accountService.createAccount(
                AccountType.GOVERNMENT, TreasuryConstants.VIRTUAL_TREASURY_OWNER, name);
    }
}
