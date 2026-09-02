package io.paradaux.jobs.services;

import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.JobResult;
import io.paradaux.jobs.api.model.HeldJob;
import io.paradaux.jobs.api.model.JobId;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hiring, firing, quitting and membership reads.
 *
 * <p>The plugin-internal counterpart of {@code JobsApi}: commands and the API both
 * come through here, so there is exactly one authority check, one write ordering and
 * one audit path regardless of entry point.</p>
 */
public interface JobService {

    CompletableFuture<JobResult> hire(UUID subject, JobId job, JobActor actor, String reason);

    CompletableFuture<JobResult> fire(UUID subject, JobId job, JobActor actor, String reason);

    /**
     * Leave a job. Never subject to the can-manage hierarchy: a player may always
     * leave any job immediately, so nobody can be trapped in one.
     */
    CompletableFuture<JobResult> quit(UUID subject, JobId job);

    /** Every job the player holds, ordered by type then configuration order. */
    CompletableFuture<List<HeldJob>> heldJobs(UUID subject);

    /** As {@link #heldJobs}, restricted to one type key. */
    CompletableFuture<List<HeldJob>> heldJobsOfType(UUID subject, String typeKey);

    CompletableFuture<Boolean> holds(UUID subject, JobId job);

    /** Cache-only and non-blocking; false for an offline or uncached player. */
    boolean holdsCached(UUID subject, JobId job);
}
