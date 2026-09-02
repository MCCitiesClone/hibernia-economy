package io.paradaux.jobs.services;

import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.model.JobDefinition;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.mappers.JobEventMapper;
import io.paradaux.jobs.mappers.JobMembershipMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What lands in the audit log and the mirror for each kind of change and actor. */
@ExtendWith(MockitoExtension.class)
class JobAuditServiceTest {

    private static final JobDefinition ELECTRICIAN = new JobDefinition(
            JobId.of("trades", "electrician"), "Electrician", "trade-electrician",
            "Wires things.", Set.of());

    @Mock private JobEventMapper events;
    @Mock private JobMembershipMapper memberships;

    private JobAuditService audit;
    private final UUID subject = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        audit = new JobAuditService(events, memberships);
    }

    @Test
    void aPlayerHireIsLoggedWithTheActorUuidAndMirrored() {
        JobActor actor = JobActor.player(actorId, "evan", false);

        audit.recordGrant(ELECTRICIAN, subject, actor,
                JobAuditService.Action.HIRE, JobAuditService.Source.COMMAND, "apprenticeship");

        verify(events).record(eq("trades"), eq("electrician"), eq("trade-electrician"),
                eq(subject.toString()), eq("HIRE"), eq("COMMAND"), eq("PLAYER"),
                eq(actorId.toString()), eq("evan"), eq(false), eq("apprenticeship"));
        verify(memberships).upsert(eq(subject.toString()), eq("trades"), eq("electrician"),
                eq("trade-electrician"), eq("jobs"));
    }

    @Test
    void nonPlayerActorsAreLoggedWithNoUuid() {
        // The ck_job_event_actor constraint requires this; getting it wrong would
        // make every console or plugin hire fail at insert time.
        audit.recordGrant(ELECTRICIAN, subject, JobActor.console(),
                JobAuditService.Action.HIRE, JobAuditService.Source.COMMAND, null);
        verify(events).record(anyString(), anyString(), anyString(), anyString(),
                eq("HIRE"), eq("COMMAND"), eq("CONSOLE"), isNull(), eq("CONSOLE"),
                eq(true), isNull());

        audit.recordGrant(ELECTRICIAN, subject, JobActor.plugin("Trades"),
                JobAuditService.Action.HIRE, JobAuditService.Source.API, null);
        verify(events).record(anyString(), anyString(), anyString(), anyString(),
                eq("HIRE"), eq("API"), eq("PLUGIN"), isNull(), eq("Trades"), eq(true), isNull());
    }

    @Test
    void anAdminHireIsFlaggedAsPrivileged() {
        audit.recordGrant(ELECTRICIAN, subject, JobActor.player(actorId, "evan", true),
                JobAuditService.Action.HIRE, JobAuditService.Source.COMMAND, null);

        // via_admin=1 is what lets an auditor find every hierarchy bypass in one query.
        verify(events).record(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(),
                eq(true), isNull());
    }

    @Test
    void aRevokeLogsAndDropsTheMirrorRow() {
        audit.recordRevoke(ELECTRICIAN, subject, JobActor.console(),
                JobAuditService.Action.FIRE, JobAuditService.Source.COMMAND, null);

        verify(events).record(anyString(), anyString(), anyString(), anyString(),
                eq("FIRE"), anyString(), anyString(), isNull(), anyString(), anyBoolean(), isNull());
        verify(memberships).delete(subject.toString(), "trades", "electrician");
    }

    @Test
    void detectedMembershipIsRecordedAsExternalBySystem() {
        // The reconciler's finding: another plugin granted the group directly.
        audit.recordDetectedAdd(ELECTRICIAN, subject);

        verify(events).record(anyString(), anyString(), anyString(), eq(subject.toString()),
                eq("DETECTED_ADD"), eq("RECONCILER"), eq("SYSTEM"), isNull(), eq("SYSTEM"),
                eq(true), isNull());
        verify(memberships).upsert(anyString(), anyString(), anyString(), anyString(),
                eq("external"));
    }

    @Test
    void detectedRemovalIsRecordedAndTheMirrorRowDropped() {
        audit.recordDetectedRemove(ELECTRICIAN, subject);

        verify(events).record(anyString(), anyString(), anyString(), anyString(),
                eq("DETECTED_REMOVE"), eq("RECONCILER"), eq("SYSTEM"), isNull(), anyString(),
                anyBoolean(), isNull());
        verify(memberships).delete(subject.toString(), "trades", "electrician");
    }

    @Test
    void touchRefreshesTheMirrorWithoutLogging() {
        audit.touch(ELECTRICIAN, subject, JobAuditService.Provenance.JOBS);

        verify(memberships).upsert(subject.toString(), "trades", "electrician",
                "trade-electrician", "jobs");
        org.mockito.Mockito.verifyNoInteractions(events);
    }

    @Test
    void anOverlongReasonIsTruncatedRatherThanFailingTheInsert() {
        // reason is VARCHAR(255); a long /hire message must not break the audit write.
        String longReason = "x".repeat(400);

        audit.recordGrant(ELECTRICIAN, subject, JobActor.console(),
                JobAuditService.Action.HIRE, JobAuditService.Source.COMMAND, longReason);

        verify(events).record(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), isNull(), anyString(), anyBoolean(),
                eq("x".repeat(255)));
    }

    @Test
    void aBlankReasonIsStoredAsNull() {
        audit.recordGrant(ELECTRICIAN, subject, JobActor.console(),
                JobAuditService.Action.HIRE, JobAuditService.Source.COMMAND, "   ");

        verify(events).record(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), isNull(), anyString(), anyBoolean(), isNull());
    }

    @Test
    void readsDelegateToTheMappers() {
        when(memberships.listSubjects("trades", "electrician")).thenReturn(java.util.List.of("a"));
        when(memberships.countExternal()).thenReturn(3);

        assertThat(audit.mirroredSubjects(ELECTRICIAN)).containsExactly("a");
        assertThat(audit.externalCount()).isEqualTo(3);

        audit.history(subject, 5);
        verify(events).recentForSubject(subject.toString(), 5);

        audit.externalMemberships(7);
        verify(memberships).listExternal(7);
    }

    @Test
    void provenanceValuesMatchTheSchemaEnum() {
        assertThat(JobAuditService.Provenance.JOBS.value()).isEqualTo("jobs");
        assertThat(JobAuditService.Provenance.EXTERNAL.value()).isEqualTo("external");
    }
}
