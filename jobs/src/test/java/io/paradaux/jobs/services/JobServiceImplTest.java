package io.paradaux.jobs.services;

import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.Outcome;
import io.paradaux.jobs.api.model.HeldJob;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.model.config.JobsYaml;
import io.paradaux.jobs.permissions.MutationOutcome;
import io.paradaux.jobs.permissions.PermissionBackend;
import io.paradaux.jobs.services.impl.JobServiceImpl;
import org.bukkit.configuration.file.YamlConfiguration;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The authority model, idempotency contract and write ordering. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobServiceImplTest {

    private static final String CONFIG = """
            types:
              government:
                display-name: "Government"
                order: 10
                jobs:
                  president:
                    group: "president"
                    can-manage: [ "government/clerk", "licenses/*" ]
                  clerk:
                    group: "clerk"
              licenses:
                display-name: "Licences"
                order: 50
                jobs:
                  firearms:
                    group: "license-firearms"
              trades:
                display-name: "Trades"
                order: 40
                managed-externally: true
                jobs:
                  electrician:
                    group: "trade-electrician"
            """;

    private static final JobId CLERK = JobId.of("government", "clerk");
    private static final JobId PRESIDENT = JobId.of("government", "president");
    private static final JobId FIREARMS = JobId.of("licenses", "firearms");
    private static final JobId ELECTRICIAN = JobId.of("trades", "electrician");

    @Mock private PermissionBackend backend;
    @Mock private JobAuditService audit;
    @Mock private JobEventPublisher publisher;

    private final Logger log = Logger.getLogger("JobServiceImplTest");
    private final UUID subject = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private JobRegistry registry;
    private JobServiceImpl service;

    @BeforeEach
    void setUp() {
        JobSnapshot snapshot = JobSnapshot.build(
                JobsYaml.parse(YamlConfiguration.loadConfiguration(new StringReader(CONFIG))), log);
        registry = new JobRegistry() {
            @Override public JobSnapshot snapshot() { return snapshot; }
            @Override public void rebuild() { }
        };

        when(backend.available()).thenReturn(true);
        when(backend.inheritedGroups(any())).thenReturn(CompletableFuture.completedFuture(Set.of()));
        when(backend.directGroups(any())).thenReturn(CompletableFuture.completedFuture(Set.of()));
        when(backend.addGroup(any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(MutationOutcome.CHANGED));
        when(backend.removeGroup(any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(MutationOutcome.CHANGED));

        GroupProvisioner provisioner = new GroupProvisioner(registry, backend, log);
        when(backend.ensureGroup(anyString())).thenReturn(CompletableFuture.completedFuture(null));

        service = new JobServiceImpl(registry, backend, provisioner, audit, publisher, log);
    }

    /** Give the actor the president job, whose selectors cover clerk and all licences. */
    private JobActor presidentActor() {
        when(backend.inheritedGroups(actorId))
                .thenReturn(CompletableFuture.completedFuture(Set.of("president")));
        return JobActor.player(actorId, "evan", false);
    }

    // ---- authority ----

    @Test
    void anActorWithAMatchingSelectorMayHire() {
        assertThat(service.hire(subject, CLERK, presidentActor(), null).join().outcome())
                .isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void aWildcardSelectorCoversEveryJobOfThatType() {
        assertThat(service.hire(subject, FIREARMS, presidentActor(), null).join().outcome())
                .isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void anActorWithNoMatchingSelectorIsRefused() {
        // The president may manage the clerk, but nothing grants authority over the
        // presidency itself.
        assertThat(service.hire(subject, PRESIDENT, presidentActor(), null).join().outcome())
                .isEqualTo(Outcome.NOT_AUTHORISED);
        verify(backend, never()).addGroup(any(), eq("president"));
        verifyNoInteractions(audit);
    }

    @Test
    void aJoblessPlayerCanHireNobody() {
        JobActor nobody = JobActor.player(actorId, "evan", false);
        assertThat(service.hire(subject, CLERK, nobody, null).join().outcome())
                .isEqualTo(Outcome.NOT_AUTHORISED);
    }

    @Test
    void theAdminPermissionBypassesTheHierarchy() {
        JobActor admin = JobActor.player(actorId, "evan", true);
        assertThat(service.hire(subject, PRESIDENT, admin, null).join().outcome())
                .isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void consoleAndPluginActorsBypassTheHierarchy() {
        assertThat(service.hire(subject, PRESIDENT, JobActor.console(), null).join().outcome())
                .isEqualTo(Outcome.SUCCESS);
        assertThat(service.hire(subject, PRESIDENT, JobActor.plugin("Trades"), null).join().outcome())
                .isEqualTo(Outcome.SUCCESS);
    }

    // ---- managed-externally ----

    @Test
    void anOrdinaryPlayerCannotHandHireAnExternallyManagedType() {
        assertThat(service.hire(subject, ELECTRICIAN, presidentActor(), null).join().outcome())
                .isEqualTo(Outcome.EXTERNALLY_MANAGED);
    }

    @Test
    void theOwningPluginAndConsoleStillHireAnExternallyManagedType() {
        // The whole point of the flag: keep players out, let the owner in.
        assertThat(service.hire(subject, ELECTRICIAN, JobActor.plugin("Trades"), null).join().outcome())
                .isEqualTo(Outcome.SUCCESS);
        assertThat(service.hire(subject, ELECTRICIAN, JobActor.console(), null).join().outcome())
                .isEqualTo(Outcome.SUCCESS);
        assertThat(service.hire(subject, ELECTRICIAN, JobActor.player(actorId, "evan", true), null)
                .join().outcome()).isEqualTo(Outcome.SUCCESS);
    }

    // ---- idempotency ----

    @Test
    void hiringSomeoneWhoAlreadyHoldsTheJobWritesNothing() {
        when(backend.addGroup(subject, "clerk"))
                .thenReturn(CompletableFuture.completedFuture(MutationOutcome.ALREADY_HAD));

        assertThat(service.hire(subject, CLERK, presidentActor(), null).join().outcome())
                .isEqualTo(Outcome.ALREADY_HELD);
        // No audit row and no event: the log records transitions, not attempts, so a
        // re-sync from an owning plugin stays free.
        verifyNoInteractions(audit);
        verifyNoInteractions(publisher);
    }

    @Test
    void firingSomeoneWhoDoesNotHoldTheJobWritesNothing() {
        when(backend.removeGroup(subject, "clerk"))
                .thenReturn(CompletableFuture.completedFuture(MutationOutcome.DID_NOT_HAVE));

        assertThat(service.fire(subject, CLERK, presidentActor(), null).join().outcome())
                .isEqualTo(Outcome.NOT_HELD);
        verifyNoInteractions(audit);
    }

    // ---- inherited membership ----

    @Test
    void anInheritedJobCannotBeRemovedAndSaysSo() {
        when(backend.removeGroup(subject, "clerk"))
                .thenReturn(CompletableFuture.completedFuture(MutationOutcome.DID_NOT_HAVE));
        when(backend.inheritedGroups(subject))
                .thenReturn(CompletableFuture.completedFuture(Set.of("clerk")));

        // Not "they don't have it" — they do, via a parent rank. The fix is a rank
        // change, so saying NOT_HELD would send the operator down the wrong path.
        assertThat(service.fire(subject, CLERK, presidentActor(), null).join().outcome())
                .isEqualTo(Outcome.INHERITED_NOT_DIRECT);
    }

    @Test
    void removingADirectNodeStillInheritedIsFlaggedInTheDetail() {
        when(backend.inheritedGroups(subject))
                .thenReturn(CompletableFuture.completedFuture(Set.of("clerk")));

        var result = service.fire(subject, CLERK, presidentActor(), null).join();
        assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(result.detailOptional()).contains("still inherited from another group");
    }

    // ---- quit ----

    @Test
    void quitNeedsNoAuthorityAtAll() {
        // A player must never be trapped in a job, whatever the hierarchy says.
        assertThat(service.quit(subject, PRESIDENT).join().outcome()).isEqualTo(Outcome.SUCCESS);
        verify(audit).recordRevoke(any(), eq(subject), any(),
                eq(JobAuditService.Action.QUIT), eq(JobAuditService.Source.COMMAND), any());
    }

    @Test
    void quittingAnExternallyManagedJobStillWorks() {
        assertThat(service.quit(subject, ELECTRICIAN).join().outcome()).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void quittingSomethingNotHeldReportsNotHeld() {
        when(backend.removeGroup(subject, "president"))
                .thenReturn(CompletableFuture.completedFuture(MutationOutcome.DID_NOT_HAVE));
        assertThat(service.quit(subject, PRESIDENT).join().outcome()).isEqualTo(Outcome.NOT_HELD);
    }

    // ---- failure handling ----

    @Test
    void anUnknownJobIsReportedWithoutTouchingTheBackend() {
        assertThat(service.hire(subject, JobId.of("nope", "nope"), JobActor.console(), null)
                .join().outcome()).isEqualTo(Outcome.UNKNOWN_JOB);
        verify(backend, never()).addGroup(any(), anyString());
    }

    @Test
    void everyOperationIsRefusedWhenLuckPermsIsUnavailable() {
        when(backend.available()).thenReturn(false);
        assertThat(service.hire(subject, CLERK, JobActor.console(), null).join().outcome())
                .isEqualTo(Outcome.PERMISSIONS_UNAVAILABLE);
        assertThat(service.fire(subject, CLERK, JobActor.console(), null).join().outcome())
                .isEqualTo(Outcome.PERMISSIONS_UNAVAILABLE);
        assertThat(service.quit(subject, CLERK).join().outcome())
                .isEqualTo(Outcome.PERMISSIONS_UNAVAILABLE);
        assertThat(service.heldJobs(subject).join()).isEmpty();
    }

    @Test
    void aBackendRefusalSurfacesAsAnError() {
        when(backend.addGroup(subject, "clerk"))
                .thenReturn(CompletableFuture.completedFuture(MutationOutcome.FAILED));
        assertThat(service.hire(subject, CLERK, presidentActor(), null).join().outcome())
                .isEqualTo(Outcome.ERROR);
    }

    @Test
    void anAuditFailureAfterASuccessfulGrantStillReportsSuccess() {
        // LuckPerms is the source of truth and it did change. Reporting failure would
        // be the inaccurate answer; the reconciler backfills the missing mirror row.
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(audit).recordGrant(any(), any(), any(), any(), any(), any());

        assertThat(service.hire(subject, CLERK, presidentActor(), null).join().outcome())
                .isEqualTo(Outcome.SUCCESS);
    }

    // ---- reads ----

    @Test
    void heldJobsAreGroupedByTypeInConfiguredOrderAndMarkInheritance() {
        when(backend.inheritedGroups(subject))
                .thenReturn(CompletableFuture.completedFuture(Set.of("president", "license-firearms")));
        when(backend.directGroups(subject))
                .thenReturn(CompletableFuture.completedFuture(Set.of("president")));

        List<HeldJob> held = service.heldJobs(subject).join();

        assertThat(held).extracting(HeldJob::id).containsExactly(PRESIDENT, FIREARMS);
        assertThat(held.get(0).direct()).isTrue();
        assertThat(held.get(1).direct()).isFalse();   // inherited from a parent rank
        assertThat(held.get(0).typeDisplayName()).isEqualTo("Government");
    }

    @Test
    void heldJobsCanBeFilteredToOneType() {
        when(backend.inheritedGroups(subject))
                .thenReturn(CompletableFuture.completedFuture(Set.of("president", "license-firearms")));

        assertThat(service.heldJobsOfType(subject, "licenses").join())
                .extracting(HeldJob::id).containsExactly(FIREARMS);
        assertThat(service.heldJobsOfType(subject, "nonexistent").join()).isEmpty();
    }

    @Test
    void holdsUsesEffectiveMembership() {
        when(backend.inheritedGroups(subject))
                .thenReturn(CompletableFuture.completedFuture(Set.of("clerk")));
        assertThat(service.holds(subject, CLERK).join()).isTrue();
        assertThat(service.holds(subject, PRESIDENT).join()).isFalse();
        assertThat(service.holds(subject, JobId.of("nope", "nope")).join()).isFalse();
    }

    @Test
    void holdsCachedReadsOnlyTheCache() {
        when(backend.cachedInheritedGroups(subject)).thenReturn(Set.of("clerk"));
        assertThat(service.holdsCached(subject, CLERK)).isTrue();
        assertThat(service.holdsCached(subject, PRESIDENT)).isFalse();
    }

    // ---- audit content ----

    @Test
    void aPrivilegedGrantIsRecordedAsSuch() {
        service.hire(subject, PRESIDENT, JobActor.plugin("Trades"), "apprenticeship").join();

        verify(audit).recordGrant(any(), eq(subject),
                org.mockito.ArgumentMatchers.argThat(JobActor::privileged),
                eq(JobAuditService.Action.HIRE), eq(JobAuditService.Source.API),
                eq("apprenticeship"));
    }

    @Test
    void aCommandHireIsRecordedAgainstTheCommandSource() {
        service.hire(subject, CLERK, presidentActor(), null).join();
        verify(audit).recordGrant(any(), eq(subject), any(),
                eq(JobAuditService.Action.HIRE), eq(JobAuditService.Source.COMMAND), any());
    }

    @Test
    void successfulTransitionsFireTheirEvents() {
        service.hire(subject, CLERK, presidentActor(), null).join();
        verify(publisher).hired(eq(subject), eq(CLERK), any(), any());

        service.fire(subject, CLERK, presidentActor(), null).join();
        verify(publisher).fired(eq(subject), eq(CLERK), any(), any());

        service.quit(subject, CLERK).join();
        verify(publisher).quit(eq(subject), eq(CLERK), any());
    }
}
