package io.paradaux.jobs.api;

import io.paradaux.jobs.api.model.JobId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Covers the invariants the API value types promise to callers. */
class JobApiTypesTest {

    // ---- JobId ----

    @Test
    void jobIdLowerCasesAndTrimsBothHalves() {
        JobId id = JobId.of("  Trades ", "Master-Electrician");
        assertThat(id.type()).isEqualTo("trades");
        assertThat(id.job()).isEqualTo("master-electrician");
        assertThat(id.qualified()).isEqualTo("trades/master-electrician");
    }

    @Test
    void jobIdsWithDifferentCasingAreEqual() {
        assertThat(JobId.of("Government", "President")).isEqualTo(JobId.of("government", "president"));
    }

    @Test
    void jobIdRejectsBlankHalves() {
        assertThatThrownBy(() -> JobId.of("", "electrician")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobId.of("trades", "  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseQualifiedAcceptsOnlyWellFormedTypeSlashJob() {
        assertThat(JobId.parseQualified("trades/electrician")).contains(JobId.of("trades", "electrician"));
        // A bare key is not resolvable without the catalogue, so it is rejected here.
        assertThat(JobId.parseQualified("electrician")).isEmpty();
        assertThat(JobId.parseQualified("/electrician")).isEmpty();
        assertThat(JobId.parseQualified("trades/")).isEmpty();
        assertThat(JobId.parseQualified("a/b/c")).isEmpty();
        assertThat(JobId.parseQualified(null)).isEmpty();
    }

    // ---- JobActor ----

    @Test
    void playerActorIsPrivilegedOnlyWithTheAdminPermission() {
        UUID id = UUID.randomUUID();
        assertThat(JobActor.player(id, "evan", false).privileged()).isFalse();
        assertThat(JobActor.player(id, "evan", true).privileged()).isTrue();
    }

    @Test
    void nonPlayerActorsAreAlwaysPrivileged() {
        // Console, plugins and the reconciler hold no jobs, so no hierarchy could apply.
        assertThat(JobActor.console().privileged()).isTrue();
        assertThat(JobActor.plugin("Trades").privileged()).isTrue();
        assertThat(JobActor.system().privileged()).isTrue();
        // Even if a caller constructs one directly asking for non-privileged.
        assertThat(new JobActor(ActorType.CONSOLE, null, "CONSOLE", false).privileged()).isTrue();
    }

    @Test
    void playerActorRequiresAUuid() {
        assertThatThrownBy(() -> new JobActor(ActorType.PLAYER, null, "evan", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pluginActorRequiresANameSoPrivilegedGrantsAreNeverAnonymous() {
        assertThatThrownBy(() -> JobActor.plugin("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(JobActor.plugin("Trades").displayName()).isEqualTo("Trades");
    }

    @Test
    void displayNameFallsBackToUuidThenType() {
        UUID id = UUID.randomUUID();
        assertThat(JobActor.player(id, null, false).displayName()).isEqualTo(id.toString());
    }

    // ---- Outcome / JobResult ----

    @Test
    void onlySuccessCountsAsSuccessful() {
        assertThat(Outcome.SUCCESS.successful()).isTrue();
        // The two idempotent no-ops are ordinary outcomes, not failures to retry.
        assertThat(Outcome.ALREADY_HELD.successful()).isFalse();
        assertThat(Outcome.NOT_HELD.successful()).isFalse();
        assertThat(Outcome.NOT_AUTHORISED.successful()).isFalse();
    }

    @Test
    void jobResultExposesOptionalContext() {
        JobId job = JobId.of("legal", "justice");
        JobResult ok = JobResult.of(Outcome.SUCCESS, job, UUID.randomUUID());
        assertThat(ok.successful()).isTrue();
        assertThat(ok.jobOptional()).contains(job);
        assertThat(ok.detailOptional()).isEmpty();

        JobResult unknown = JobResult.of(Outcome.UNKNOWN_JOB, null, null, "no such job: wizard");
        assertThat(unknown.jobOptional()).isEmpty();
        assertThat(unknown.detailOptional()).contains("no such job: wizard");
    }
}
