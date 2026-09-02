package io.paradaux.jobs.api;

import io.paradaux.jobs.api.impl.JobsApiImpl;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.services.JobService;
import io.paradaux.jobs.services.JobSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The public API is a pure adapter: every call must reach the same service the
 * in-game commands use, so an external plugin and a player get identical behaviour.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobsApiImplTest {

    @Mock private JobService jobs;
    @Mock private JobRegistry registry;

    private JobsApi api;
    private final UUID subject = UUID.randomUUID();
    private final JobId job = JobId.of("trades", "electrician");

    @BeforeEach
    void setUp() {
        api = new JobsApiImpl(jobs, registry);
        when(jobs.hire(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(JobResult.of(Outcome.SUCCESS, job, subject)));
        when(jobs.fire(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(JobResult.of(Outcome.SUCCESS, job, subject)));
        when(jobs.holds(any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        when(jobs.heldJobs(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(jobs.heldJobsOfType(any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(jobs.holdsCached(any(), any())).thenReturn(true);
    }

    @Test
    void theCatalogueIsTheCurrentSnapshot() {
        JobSnapshot snapshot = JobSnapshot.empty();
        when(registry.snapshot()).thenReturn(snapshot);

        // Read straight off the AtomicReference, which is what makes it safe to call
        // from the main thread while the mutating calls return futures.
        assertThat(api.catalog()).isSameAs(snapshot);
    }

    @Test
    void mutationsDelegateWithTheActorIntact() {
        JobActor actor = JobActor.plugin("Trades");

        assertThat(api.hire(subject, job, actor, "earned").join().successful()).isTrue();
        verify(jobs).hire(eq(subject), eq(job), eq(actor), eq("earned"));

        assertThat(api.fire(subject, job, actor, null).join().successful()).isTrue();
        verify(jobs).fire(eq(subject), eq(job), eq(actor), eq(null));
    }

    @Test
    void readsDelegate() {
        assertThat(api.holds(subject, job).join()).isTrue();
        verify(jobs).holds(subject, job);

        api.jobsOf(subject).join();
        verify(jobs).heldJobs(subject);

        api.jobsOf(subject, "trades").join();
        verify(jobs).heldJobsOfType(subject, "trades");

        assertThat(api.holdsCached(subject, job)).isTrue();
        verify(jobs).holdsCached(subject, job);
    }
}
