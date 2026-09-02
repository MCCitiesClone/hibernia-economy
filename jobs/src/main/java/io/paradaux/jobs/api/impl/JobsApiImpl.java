package io.paradaux.jobs.api.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.JobCatalog;
import io.paradaux.jobs.api.JobResult;
import io.paradaux.jobs.api.JobsApi;
import io.paradaux.jobs.api.model.HeldJob;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.services.JobService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Adapts the internal {@link JobService} to the public {@link JobsApi}.
 *
 * <p>Thin on purpose: it exposes no internals and adds no behaviour, so a command and
 * an external plugin performing the same action go through identical code — one
 * authority check, one write ordering, one audit path.</p>
 */
@Singleton
public final class JobsApiImpl implements JobsApi {

    private final JobService jobs;
    private final JobRegistry registry;

    @Inject
    public JobsApiImpl(JobService jobs, JobRegistry registry) {
        this.jobs = jobs;
        this.registry = registry;
    }

    @Override
    public JobCatalog catalog() {
        // The snapshot is itself the catalogue: an immutable in-memory view, so this
        // is safe to call from the main thread.
        return registry.snapshot();
    }

    @Override
    public CompletableFuture<JobResult> hire(UUID subject, JobId job, JobActor actor, String reason) {
        return jobs.hire(subject, job, actor, reason);
    }

    @Override
    public CompletableFuture<JobResult> fire(UUID subject, JobId job, JobActor actor, String reason) {
        return jobs.fire(subject, job, actor, reason);
    }

    @Override
    public CompletableFuture<Boolean> holds(UUID subject, JobId job) {
        return jobs.holds(subject, job);
    }

    @Override
    public CompletableFuture<List<HeldJob>> jobsOf(UUID subject) {
        return jobs.heldJobs(subject);
    }

    @Override
    public CompletableFuture<List<HeldJob>> jobsOf(UUID subject, String typeKey) {
        return jobs.heldJobsOfType(subject, typeKey);
    }

    @Override
    public boolean holdsCached(UUID subject, JobId job) {
        return jobs.holdsCached(subject, job);
    }
}
