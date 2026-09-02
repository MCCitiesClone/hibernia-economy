package io.paradaux.jobs.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.JobResult;
import io.paradaux.jobs.api.Outcome;
import io.paradaux.jobs.api.model.HeldJob;
import io.paradaux.jobs.api.model.JobDefinition;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.api.model.JobType;
import io.paradaux.jobs.permissions.MutationOutcome;
import io.paradaux.jobs.permissions.PermissionBackend;
import io.paradaux.jobs.services.CanManageMatcher;
import io.paradaux.jobs.services.GroupProvisioner;
import io.paradaux.jobs.services.JobAuditService;
import io.paradaux.jobs.services.JobEventPublisher;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.services.JobService;
import io.paradaux.jobs.services.JobSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The one place a job membership changes.
 *
 * <h2>Write ordering</h2>
 * <p>LuckPerms first, the database second. LuckPerms is the source of truth, so a
 * database failure after a successful group write leaves a <em>gap in the log</em>,
 * which the reconciler fills on its next pass. The reverse order would leave a
 * <em>lie</em> in the log — a recorded hire that never happened. The operation
 * therefore reports success when the authoritative state changed, and logs the audit
 * failure loudly rather than pretending the hire failed.</p>
 *
 * <h2>Idempotency</h2>
 * <p>Hiring someone who already holds a job writes nothing: no save, no audit row, no
 * event. The log records transitions, not attempts, which is what makes a periodic
 * re-sync from an owning plugin free.</p>
 */
@Singleton
public final class JobServiceImpl implements JobService {

    private final JobRegistry registry;
    private final PermissionBackend backend;
    private final GroupProvisioner provisioner;
    private final JobAuditService audit;
    private final JobEventPublisher publisher;
    private final Logger log;

    @Inject
    public JobServiceImpl(JobRegistry registry, PermissionBackend backend,
                          GroupProvisioner provisioner, JobAuditService audit,
                          JobEventPublisher publisher,
                          org.bukkit.plugin.java.JavaPlugin plugin) {
        this(registry, backend, provisioner, audit, publisher, plugin.getLogger());
    }

    /** Test seam: no Bukkit plugin required. */
    public JobServiceImpl(JobRegistry registry, PermissionBackend backend,
                          GroupProvisioner provisioner, JobAuditService audit,
                          JobEventPublisher publisher, Logger log) {
        this.registry = registry;
        this.backend = backend;
        this.provisioner = provisioner;
        this.audit = audit;
        this.publisher = publisher;
        this.log = log;
    }

    // ---- writes ----

    @Override
    public CompletableFuture<JobResult> hire(UUID subject, JobId job, JobActor actor, String reason) {
        JobSnapshot snapshot = registry.snapshot();
        JobDefinition definition = snapshot.job(job).orElse(null);
        if (definition == null) {
            return completed(Outcome.UNKNOWN_JOB, job, subject);
        }
        if (!backend.available()) {
            return completed(Outcome.PERMISSIONS_UNAVAILABLE, job, subject);
        }

        return authorise(snapshot, definition, actor).thenCompose(refusal -> {
            if (refusal != null) {
                return completed(refusal, job, subject);
            }
            // Provision immediately before the grant, so a job added to jobs.yml and
            // hired before a reload still lands on a group that exists.
            return provisioner.ensure(job)
                    .thenCompose(ignored -> backend.addGroup(subject, definition.group()))
                    .thenApply(outcome -> switch (outcome) {
                        case ALREADY_HAD -> JobResult.of(Outcome.ALREADY_HELD, job, subject);
                        case CHANGED -> {
                            persist(() -> audit.recordGrant(definition, subject, actor,
                                    JobAuditService.Action.HIRE, sourceOf(actor), reason));
                            publisher.hired(subject, job, actor, reason);
                            yield JobResult.of(Outcome.SUCCESS, job, subject);
                        }
                        default -> JobResult.of(Outcome.ERROR, job, subject,
                                "the permission backend refused the change");
                    });
        });
    }

    @Override
    public CompletableFuture<JobResult> fire(UUID subject, JobId job, JobActor actor, String reason) {
        JobSnapshot snapshot = registry.snapshot();
        JobDefinition definition = snapshot.job(job).orElse(null);
        if (definition == null) {
            return completed(Outcome.UNKNOWN_JOB, job, subject);
        }
        if (!backend.available()) {
            return completed(Outcome.PERMISSIONS_UNAVAILABLE, job, subject);
        }

        return authorise(snapshot, definition, actor).thenCompose(refusal -> {
            if (refusal != null) {
                return completed(refusal, job, subject);
            }
            return removeAndRecord(subject, definition, actor,
                    JobAuditService.Action.FIRE, sourceOf(actor), reason)
                    .thenApply(result -> {
                        if (result.outcome() == Outcome.SUCCESS) {
                            publisher.fired(subject, job, actor, reason);
                        }
                        return result;
                    });
        });
    }

    @Override
    public CompletableFuture<JobResult> quit(UUID subject, JobId job) {
        JobSnapshot snapshot = registry.snapshot();
        JobDefinition definition = snapshot.job(job).orElse(null);
        if (definition == null) {
            return completed(Outcome.UNKNOWN_JOB, job, subject);
        }
        if (!backend.available()) {
            return completed(Outcome.PERMISSIONS_UNAVAILABLE, job, subject);
        }

        // No authority check of any kind: quitting always works, immediately, for
        // every type. A player can never be trapped in a job.
        JobActor actor = JobActor.player(subject, null, false);
        return removeAndRecord(subject, definition, actor,
                JobAuditService.Action.QUIT, JobAuditService.Source.COMMAND, null)
                .thenApply(result -> {
                    if (result.outcome() == Outcome.SUCCESS) {
                        publisher.quit(subject, job, actor);
                    }
                    return result;
                });
    }

    private CompletableFuture<JobResult> removeAndRecord(UUID subject, JobDefinition definition,
                                                         JobActor actor, JobAuditService.Action action,
                                                         JobAuditService.Source source, String reason) {
        JobId job = definition.id();
        return backend.removeGroup(subject, definition.group()).thenCompose(outcome -> switch (outcome) {
            case DID_NOT_HAVE -> stillInheritedResult(subject, definition);
            case CHANGED -> {
                persist(() -> audit.recordRevoke(definition, subject, actor, action, source, reason));
                // The direct node is gone, but a parent rank may still grant the
                // group. Saying "removed" without qualification would be a lie.
                yield backend.inheritedGroups(subject).thenApply(groups ->
                        groups.contains(definition.group())
                                ? JobResult.of(Outcome.SUCCESS, job, subject,
                                        "still inherited from another group")
                                : JobResult.of(Outcome.SUCCESS, job, subject));
            }
            default -> CompletableFuture.completedFuture(
                    JobResult.of(Outcome.ERROR, job, subject,
                            "the permission backend refused the change"));
        });
    }

    /**
     * Distinguish "does not hold it at all" from "holds it only by inheritance",
     * because the fix for the second is a rank change, not another attempt here.
     */
    private CompletableFuture<JobResult> stillInheritedResult(UUID subject, JobDefinition definition) {
        return backend.inheritedGroups(subject).thenApply(groups ->
                groups.contains(definition.group())
                        ? JobResult.of(Outcome.INHERITED_NOT_DIRECT, definition.id(), subject)
                        : JobResult.of(Outcome.NOT_HELD, definition.id(), subject));
    }

    // ---- authority ----

    /**
     * @return the refusal outcome, or {@code null} when the actor may proceed.
     */
    private CompletableFuture<Outcome> authorise(JobSnapshot snapshot, JobDefinition definition,
                                                 JobActor actor) {
        JobType type = snapshot.type(definition.id().type()).orElse(null);

        // Privileged actors — console, another plugin, the reconciler, or a player
        // holding the admin permission — bypass both the hierarchy and the
        // managed-externally guard. A plugin that owns a type must still be able to
        // grant it, and that is exactly what the flag is protecting against players
        // doing by hand.
        if (actor.privileged()) {
            return CompletableFuture.completedFuture(null);
        }
        if (type != null && type.managedExternally()) {
            return CompletableFuture.completedFuture(Outcome.EXTERNALLY_MANAGED);
        }
        if (actor.uuid() == null) {
            return CompletableFuture.completedFuture(Outcome.NOT_AUTHORISED);
        }

        return backend.inheritedGroups(actor.uuid()).thenApply(groups -> {
            Set<String> selectors = selectorsFor(snapshot, groups);
            return CanManageMatcher.canManage(selectors, definition.id())
                    ? null : Outcome.NOT_AUTHORISED;
        });
    }

    /** Every can-manage selector granted by the jobs the actor actually holds. */
    private static Set<String> selectorsFor(JobSnapshot snapshot, Set<String> groups) {
        Set<String> selectors = new HashSet<>();
        for (String group : groups) {
            snapshot.byGroup(group)
                    .flatMap(snapshot::job)
                    .ifPresent(definition -> selectors.addAll(definition.canManage()));
        }
        return selectors;
    }

    // ---- reads ----

    @Override
    public CompletableFuture<List<HeldJob>> heldJobs(UUID subject) {
        return heldJobsOfType(subject, null);
    }

    @Override
    public CompletableFuture<List<HeldJob>> heldJobsOfType(UUID subject, String typeKey) {
        JobSnapshot snapshot = registry.snapshot();
        if (!backend.available()) {
            return CompletableFuture.completedFuture(List.of());
        }
        // Listing uses effective membership: a job inherited from a parent rank is
        // real authority and hiding it would misrepresent the player.
        return backend.inheritedGroups(subject).thenCombine(backend.directGroups(subject),
                (inherited, direct) -> collect(snapshot, inherited, direct, typeKey));
    }

    private static List<HeldJob> collect(JobSnapshot snapshot, Set<String> inherited,
                                         Set<String> direct, String typeKey) {
        List<HeldJob> held = new ArrayList<>();
        for (JobType type : snapshot.types()) {
            if (typeKey != null && !type.key().equalsIgnoreCase(typeKey)) {
                continue;
            }
            for (JobId id : type.jobs()) {
                JobDefinition definition = snapshot.job(id).orElse(null);
                if (definition == null || !inherited.contains(definition.group())) {
                    continue;
                }
                held.add(new HeldJob(id, definition.displayName(), type.key(),
                        type.displayName(), direct.contains(definition.group())));
            }
        }
        return List.copyOf(held);
    }

    @Override
    public CompletableFuture<Boolean> holds(UUID subject, JobId job) {
        Optional<JobDefinition> definition = registry.snapshot().job(job);
        if (definition.isEmpty() || !backend.available()) {
            return CompletableFuture.completedFuture(false);
        }
        return backend.inheritedGroups(subject)
                .thenApply(groups -> groups.contains(definition.get().group()));
    }

    @Override
    public boolean holdsCached(UUID subject, JobId job) {
        return registry.snapshot().job(job)
                .map(definition -> backend.cachedInheritedGroups(subject).contains(definition.group()))
                .orElse(false);
    }

    // ---- helpers ----

    private static JobAuditService.Source sourceOf(JobActor actor) {
        return switch (actor.type()) {
            case PLUGIN -> JobAuditService.Source.API;
            case SYSTEM -> JobAuditService.Source.RECONCILER;
            default -> JobAuditService.Source.COMMAND;
        };
    }

    /**
     * Run an audit write, converting a failure into a loud log line rather than a
     * failed operation — the authoritative LuckPerms change has already happened, so
     * reporting failure to the caller would be the inaccurate answer.
     */
    private void persist(Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "The job membership changed in LuckPerms but the audit write "
                    + "failed; the reconciler will backfill the mirror on its next pass: "
                    + e.getMessage(), e);
        }
    }

    private static CompletableFuture<JobResult> completed(Outcome outcome, JobId job, UUID subject) {
        return CompletableFuture.completedFuture(JobResult.of(outcome, job, subject));
    }
}
