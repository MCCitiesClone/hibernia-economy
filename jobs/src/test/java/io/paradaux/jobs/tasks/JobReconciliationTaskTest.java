package io.paradaux.jobs.tasks;

import io.paradaux.jobs.api.model.JobDefinition;
import io.paradaux.jobs.model.config.JobsYaml;
import io.paradaux.jobs.permissions.PermissionBackend;
import io.paradaux.jobs.services.JobAuditService;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.services.JobSnapshot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The reconciler's contract: repair our own record of LuckPerms, and never LuckPerms
 * itself.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobReconciliationTaskTest {

    private static final String CONFIG = """
            types:
              trades:
                jobs:
                  electrician:
                    group: "trade-electrician"
            """;

    @Mock private JavaPlugin plugin;
    @Mock private PermissionBackend backend;
    @Mock private JobAuditService audit;

    private final Logger log = Logger.getLogger("JobReconciliationTaskTest");
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private JobDefinition electrician;
    private JobReconciliationTask task;

    @BeforeEach
    void setUp() {
        JobSnapshot snapshot = JobSnapshot.build(
                JobsYaml.parse(YamlConfiguration.loadConfiguration(new StringReader(CONFIG))), log);
        electrician = snapshot.job(io.paradaux.jobs.api.model.JobId.of("trades", "electrician"))
                .orElseThrow();

        JobRegistry registry = new JobRegistry() {
            @Override public JobSnapshot snapshot() { return snapshot; }
            @Override public void rebuild() { }
        };

        when(plugin.getLogger()).thenReturn(log);
        when(backend.available()).thenReturn(true);
        task = new JobReconciliationTask(plugin, registry, backend, audit);
    }

    private void luckPermsHolders(UUID... holders) {
        when(backend.directHolders("trade-electrician"))
                .thenReturn(CompletableFuture.completedFuture(Set.of(holders)));
    }

    private void mirrorHolds(UUID... holders) {
        when(audit.mirroredSubjects(any()))
                .thenReturn(java.util.Arrays.stream(holders).map(UUID::toString).toList());
    }

    @Test
    void membershipInLuckPermsButNotTheMirrorIsRecordedAsExternal() {
        // Exactly what happens when the trades plugin grants a group directly.
        luckPermsHolders(alice);
        mirrorHolds();

        task.reconcile();

        verify(audit).recordDetectedAdd(eq(electrician), eq(alice));
        verify(audit, never()).recordDetectedRemove(any(), any());
    }

    @Test
    void membershipInTheMirrorButNotLuckPermsIsRecordedAsRemoved() {
        luckPermsHolders(alice);
        mirrorHolds(alice, bob);

        task.reconcile();

        verify(audit).recordDetectedRemove(eq(electrician), eq(bob));
        verify(audit, never()).recordDetectedAdd(any(), any());
    }

    @Test
    void agreementProducesNoEvents() {
        luckPermsHolders(alice);
        mirrorHolds(alice);

        task.reconcile();

        verify(audit, never()).recordDetectedAdd(any(), any());
        verify(audit, never()).recordDetectedRemove(any(), any());
    }

    @Test
    void theReconcilerNeverWritesToLuckPerms() {
        // The central invariant. Stripping an externally-granted group would put this
        // plugin in a write-fight with whichever plugin granted it.
        luckPermsHolders(alice);
        mirrorHolds(bob);

        task.reconcile();

        verify(backend, never()).addGroup(any(), anyString());
        verify(backend, never()).removeGroup(any(), anyString());
        verify(backend, never()).ensureGroup(anyString());
    }

    @Test
    void zeroHoldersAgainstANonEmptyMirrorSkipsRemovals() {
        // A storage hiccup looks exactly like "everyone was fired at once". Emitting
        // a DETECTED_REMOVE per holder would corrupt the audit log wholesale.
        luckPermsHolders();
        mirrorHolds(alice, bob);

        task.reconcile();

        verify(audit, never()).recordDetectedRemove(any(), any());
    }

    @Test
    void zeroHoldersAgainstAnEmptyMirrorIsSimplyNothingToDo() {
        luckPermsHolders();
        mirrorHolds();

        task.reconcile();

        verify(audit, never()).recordDetectedRemove(any(), any());
        verify(audit, never()).recordDetectedAdd(any(), any());
    }

    @Test
    void aBackendFailureForOneJobDoesNotAbortThePass() {
        when(backend.directHolders("trade-electrician"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("storage down")));
        mirrorHolds(alice);

        task.reconcile();   // must not throw

        verify(audit, never()).recordDetectedRemove(any(), any());
    }

    @Test
    void nothingHappensWhenLuckPermsIsUnavailable() {
        when(backend.available()).thenReturn(false);

        task.reconcile();

        verifyNoInteractions(audit);
    }

    @Test
    void anUnparseableMirrorUuidIsSkippedRatherThanFatal() {
        luckPermsHolders(alice);
        when(audit.mirroredSubjects(any())).thenReturn(List.of("not-a-uuid", alice.toString()));

        task.reconcile();

        // alice agrees on both sides; the junk row is ignored, not crashed on.
        verify(audit, never()).recordDetectedAdd(any(), any());
        verify(audit, never()).recordDetectedRemove(any(), any());
    }
}
