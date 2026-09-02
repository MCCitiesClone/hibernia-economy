package io.paradaux.jobs.tasks;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.jobs.api.model.JobDefinition;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.permissions.PermissionBackend;
import io.paradaux.jobs.services.JobAuditService;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.services.JobSnapshot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps this plugin's mirror and audit log in step with LuckPerms.
 *
 * <h2>It never writes to LuckPerms</h2>
 * <p>LuckPerms is the source of truth, so what drifts — and what gets repaired — is
 * <em>our</em> record of it, never the permissions themselves. This matters
 * concretely: trades are granted by another plugin, and a reconciler that "corrected"
 * those by stripping the group would put the two plugins in a write-fight on a
 * half-hourly loop, revoking players' jobs at random. Membership we did not grant is
 * recorded as {@code external} and left alone; {@code /jobs audit external} surfaces
 * it so an operator can decide whether to move that plugin onto the JobsApi.</p>
 *
 * <p>Runs entirely off the main thread — it touches only LuckPerms and JDBC, never
 * Bukkit state — with every storage call time-bounded and an overlap guard, following
 * Treasury's group-reconciliation cron.</p>
 */
@Singleton
public final class JobReconciliationTask extends BukkitRunnable {

    private static final long TIMEOUT_SECONDS = 15L;

    private final JavaPlugin plugin;
    private final JobRegistry registry;
    private final PermissionBackend backend;
    private final JobAuditService audit;
    private final Logger log;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Inject
    public JobReconciliationTask(JavaPlugin plugin, JobRegistry registry,
                                 PermissionBackend backend, JobAuditService audit) {
        this.plugin = plugin;
        this.registry = registry;
        this.backend = backend;
        this.audit = audit;
        this.log = plugin.getLogger();
    }

    /** Schedule the cron. Ticks are 20 per second. */
    public void schedule(long intervalSeconds) {
        long ticks = Math.max(1L, intervalSeconds) * 20L;
        runTaskTimerAsynchronously(plugin, ticks, ticks);
        log.info("Job reconciliation scheduled every " + intervalSeconds + "s.");
    }

    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) {
            // A previous tick is still going — a slow storage backend must not stack
            // overlapping passes on top of each other.
            log.fine("Job reconciliation is already running; skipping this tick.");
            return;
        }
        try {
            reconcile();
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Job reconciliation failed: " + e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    /** One full pass. Package-visible so tests can drive it without a scheduler. */
    void reconcile() {
        if (!backend.available()) {
            return;
        }
        JobSnapshot snapshot = registry.snapshot();
        int added = 0;
        int removed = 0;

        for (JobId id : snapshot.jobs()) {
            JobDefinition definition = snapshot.job(id).orElse(null);
            if (definition == null) {
                continue;
            }
            try {
                Set<UUID> actual = await(definition.group());
                Set<UUID> mirrored = mirrored(definition);

                Set<UUID> toAdd = new LinkedHashSet<>(actual);
                toAdd.removeAll(mirrored);
                Set<UUID> toRemove = new LinkedHashSet<>(mirrored);
                toRemove.removeAll(actual);

                for (UUID subject : toAdd) {
                    audit.recordDetectedAdd(definition, subject);
                    added++;
                }

                // Mass-revoke guard: if LuckPerms reports nobody at all while the
                // mirror has holders, that is far more likely a storage hiccup than
                // everyone being fired at once. Skip removals rather than emitting a
                // flood of bogus DETECTED_REMOVE rows.
                if (actual.isEmpty() && !mirrored.isEmpty()) {
                    log.warning("LuckPerms reported no holders of '" + definition.group()
                            + "' while " + mirrored.size() + " are mirrored; skipping removals "
                            + "for " + id.qualified() + " this tick.");
                    continue;
                }

                for (UUID subject : toRemove) {
                    audit.recordDetectedRemove(definition, subject);
                    removed++;
                }
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to reconcile " + id.qualified() + ": "
                        + e.getMessage(), e);
            }
        }

        if (added > 0 || removed > 0) {
            log.info("Job reconciliation recorded " + added + " added and " + removed
                    + " removed membership(s). Use /jobs audit external to review grants "
                    + "made outside this plugin.");
        }
    }

    private Set<UUID> mirrored(JobDefinition definition) {
        Set<UUID> subjects = new HashSet<>();
        for (String raw : audit.mirroredSubjects(definition)) {
            try {
                subjects.add(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                log.warning("Ignoring an unparseable subject uuid in job_membership: " + raw);
            }
        }
        return subjects;
    }

    private Set<UUID> await(String group) {
        try {
            return backend.directHolders(group).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reconciling " + group, e);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
