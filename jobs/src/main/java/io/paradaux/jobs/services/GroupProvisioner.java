package io.paradaux.jobs.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.jobs.api.model.JobDefinition;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.permissions.PermissionBackend;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates the LuckPerms groups the configuration declares, and applies their
 * declared metadata.
 *
 * <p>Runs at three moments, which between them close every window in which a
 * configured job could have no group:</p>
 * <ol>
 *   <li><b>At enable</b>, once, off the main thread — the bulk pass.</li>
 *   <li><b>On {@code /jobs reload}</b>, so a job added to {@code jobs.yml} works
 *       without a restart.</li>
 *   <li><b>Lazily on each hire</b>, immediately before the group is granted. Nearly
 *       free once the group is cached, and it covers the operator who edits the file
 *       and hires before reloading.</li>
 * </ol>
 *
 * <p>Failures are logged and skipped per group rather than aborting the pass: one
 * unwritable group must not prevent the other twenty from being created.</p>
 */
@Singleton
public final class GroupProvisioner {

    /** Bound on every storage round-trip, matching Treasury's reconciliation cron. */
    private static final long TIMEOUT_SECONDS = 15L;

    private final JobRegistry registry;
    private final PermissionBackend backend;
    private final Logger log;

    @Inject
    public GroupProvisioner(JobRegistry registry, PermissionBackend backend,
                            org.bukkit.plugin.java.JavaPlugin plugin) {
        this(registry, backend, plugin.getLogger());
    }

    /** Test seam: no Bukkit plugin required. */
    public GroupProvisioner(JobRegistry registry, PermissionBackend backend, Logger log) {
        this.registry = registry;
        this.backend = backend;
        this.log = log;
    }

    /** What a bulk pass did, for logging and for tests. */
    public record ProvisionReport(int considered, int succeeded, int failed) {
        public boolean clean() {
            return failed == 0;
        }
    }

    /**
     * Ensure every configured group exists and carries its declared metadata.
     *
     * <p>Blocking and sequential by design: it is called from an async task at
     * startup, and a bounded loop is far easier to reason about (and to bound in
     * time) than a fan-out of chained futures.</p>
     */
    public ProvisionReport provisionAll() {
        JobSnapshot snapshot = registry.snapshot();
        if (!snapshot.provisionGroups()) {
            log.fine("Group provisioning is disabled in jobs.yml; skipping.");
            return new ProvisionReport(0, 0, 0);
        }
        if (!backend.available()) {
            log.warning("LuckPerms is unavailable; skipping group provisioning. "
                    + "Configured job groups will not be created.");
            return new ProvisionReport(0, 0, 0);
        }

        int considered = 0;
        int succeeded = 0;
        int failed = 0;
        for (JobId id : snapshot.jobs()) {
            JobDefinition definition = snapshot.job(id).orElse(null);
            if (definition == null) {
                continue;
            }
            considered++;
            try {
                await(backend.ensureGroup(definition.group()));
                await(backend.applyMetadata(definition.group(), snapshot.provisioning(id),
                        definition.color()));
                succeeded++;
            } catch (RuntimeException e) {
                failed++;
                log.log(Level.WARNING, "Failed to provision LuckPerms group '"
                        + definition.group() + "' for " + id.qualified() + ": " + e.getMessage(), e);
            }
        }

        if (considered > 0) {
            log.info("Provisioned " + succeeded + "/" + considered + " job groups"
                    + (failed > 0 ? " (" + failed + " failed — see above)" : "") + ".");
        }
        return new ProvisionReport(considered, succeeded, failed);
    }

    /**
     * Ensure one group exists and is up to date, on the hire path.
     *
     * <p>Returns a future so the caller can chain it ahead of the grant without
     * blocking; failures propagate so a hire never silently lands on a group that
     * does not exist.</p>
     */
    public CompletableFuture<Void> ensure(JobId id) {
        JobSnapshot snapshot = registry.snapshot();
        JobDefinition definition = snapshot.job(id).orElse(null);
        if (definition == null || !backend.available()) {
            return CompletableFuture.completedFuture(null);
        }
        return backend.ensureGroup(definition.group());
    }

    private static void await(CompletableFuture<?> future) {
        try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while provisioning", e);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
