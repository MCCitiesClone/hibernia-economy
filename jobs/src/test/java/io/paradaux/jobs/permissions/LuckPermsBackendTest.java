package io.paradaux.jobs.permissions;

import io.paradaux.jobs.model.config.ProvisionSettings;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.UserManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the parts of the LuckPerms adapter that can run without LuckPerms.
 *
 * <h2>What is deliberately not tested here</h2>
 * <p>The membership read and write paths cannot be unit-tested at all. LuckPerms'
 * node builders are static factories that resolve through
 * {@code LuckPermsProvider.get()}, so merely calling
 * {@code InheritanceNode.builder(group)} throws {@code NotLoadedException} unless a
 * real LuckPerms plugin has enabled. Mocking the {@code LuckPerms} instance does not
 * help, because the builder never consults it.</p>
 *
 * <p>That is precisely why {@code LuckPermsBackend} is excluded from the coverage
 * gate in {@code build.gradle.kts}, on the same grounds as Bukkit listener glue. The
 * behaviour that matters — the {@code DataMutateResult} to {@link MutationOutcome}
 * mapping and the offline-safe {@code modifyUser} write — is verified against a live
 * server, and every consumer of it is unit-tested against a mocked
 * {@link PermissionBackend} instead. Claiming unit coverage of this class would be
 * claiming coverage we do not have.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LuckPermsBackendTest {

    @Mock private LuckPerms luckPerms;
    @Mock private UserManager userManager;
    @Mock private GroupManager groupManager;
    @Mock private Group group;

    private LuckPermsBackend backend;

    @BeforeEach
    void setUp() {
        when(luckPerms.getUserManager()).thenReturn(userManager);
        when(luckPerms.getGroupManager()).thenReturn(groupManager);
        backend = new LuckPermsBackend(luckPerms);
    }

    @Test
    void theBackendReportsItselfAvailable() {
        assertThat(backend.available()).isTrue();
    }

    @Test
    void ensureGroupSkipsStorageWhenTheGroupIsAlreadyCached() {
        // Provisioning calls this once per job at startup and again on every hire, so
        // the cache check has to come before any storage round-trip.
        when(groupManager.getGroup("electrician")).thenReturn(group);

        backend.ensureGroup("electrician").join();

        verify(groupManager, never()).loadGroup(anyString());
        verify(groupManager, never()).createAndLoadGroup(anyString());
    }

    @Test
    void ensureGroupCreatesTheGroupWhenItExistsNowhere() {
        when(groupManager.getGroup("new-job")).thenReturn(null);
        when(groupManager.loadGroup("new-job"))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(groupManager.createAndLoadGroup("new-job"))
                .thenReturn(CompletableFuture.completedFuture(group));

        backend.ensureGroup("new-job").join();

        verify(groupManager).createAndLoadGroup("new-job");
    }

    @Test
    void ensureGroupDoesNotRecreateAGroupThatExistsOnlyInStorage() {
        // Not in the cache but present in storage: creating it again would reset it.
        when(groupManager.getGroup("stored")).thenReturn(null);
        when(groupManager.loadGroup("stored"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(group)));

        backend.ensureGroup("stored").join();

        verify(groupManager, never()).createAndLoadGroup(anyString());
    }

    @Test
    void applyMetadataTouchesNothingWhenNothingIsDeclared() {
        // The core promise of the provisioning model: undeclared metadata is left
        // exactly as LuckPerms has it, so hand-tuned values survive a reload.
        backend.applyMetadata("electrician", ProvisionSettings.empty()).join();
        backend.applyMetadata("electrician", null).join();

        verify(groupManager, never()).modifyGroup(anyString(), any());
    }

    @Test
    void applyMetadataModifiesTheGroupWhenSomethingIsDeclared() {
        when(groupManager.modifyGroup(eq("electrician"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        backend.applyMetadata("electrician",
                new ProvisionSettings("10", "[Sparky] ", "<yellow>", List.of("jobs.wire"))).join();

        verify(groupManager).modifyGroup(eq("electrician"), any());
    }
}
