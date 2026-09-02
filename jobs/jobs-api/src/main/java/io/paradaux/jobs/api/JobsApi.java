package io.paradaux.jobs.api;

import io.paradaux.jobs.api.model.HeldJob;
import io.paradaux.jobs.api.model.JobId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for job, licence and qualification management.
 *
 * <p>Obtain it from the Bukkit services manager, and declare {@code depend: [ Jobs ]}
 * in your {@code plugin.yml}:</p>
 * <pre>{@code
 * RegisteredServiceProvider<JobsApi> rsp =
 *         Bukkit.getServicesManager().getRegistration(JobsApi.class);
 * JobsApi jobs = rsp.getProvider();
 *
 * jobs.hire(playerId, JobId.of("trades", "electrician"),
 *           JobActor.plugin("Trades"), "Completed apprenticeship")
 *     .thenAccept(result -> {
 *         if (result.outcome() == Outcome.ALREADY_HELD) return;  // re-sync, not an error
 *         if (!result.successful()) getLogger().warning("hire failed: " + result.outcome());
 *     });
 * }</pre>
 *
 * <h2>Threading</h2>
 * <p>Membership reads and writes return a {@link CompletableFuture} because the
 * underlying LuckPerms operations are irreducibly asynchronous — the only
 * offline-safe write primitive returns a future, and a blocking call would stall the
 * server tick. Futures complete on LuckPerms' executor, so <strong>hop back to the
 * main thread</strong> with {@code Bukkit.getScheduler().runTask(plugin, ...)} before
 * touching worlds, entities or inventories in a callback.</p>
 *
 * <p>There are deliberately no blocking convenience overloads, so nobody can
 * accidentally {@code join()} on the tick. The two synchronous members —
 * {@link #catalog()} and {@link #holdsCached(UUID, JobId)} — are pure in-memory reads
 * and are documented as such.</p>
 *
 * <h2>Authority</h2>
 * <p>See {@link JobActor}. A {@link JobActor#plugin(String)} actor bypasses the
 * can-manage hierarchy, since a plugin has no jobs of its own; a
 * {@link JobActor#player(UUID, String, boolean)} actor is subject to exactly the same
 * checks as the in-game command. Both are recorded in the audit log.</p>
 *
 * <h2>Idempotency</h2>
 * <p>Hiring someone who already holds a job returns {@link Outcome#ALREADY_HELD}
 * without writing anything or logging an event — the audit log records transitions,
 * not attempts. A periodic re-sync from an owning plugin is therefore free and
 * produces no log noise.</p>
 */
public interface JobsApi {

    /**
     * The configured jobs and types. A synchronous in-memory read, safe on any
     * thread including the main thread.
     */
    @NotNull JobCatalog catalog();

    /**
     * Grant {@code job} to {@code subject}. Works whether or not the player is online.
     *
     * @param actor  who is hiring; see {@link JobActor} for the authority rules
     * @param reason optional free text stored on the audit row
     */
    @NotNull CompletableFuture<JobResult> hire(@NotNull UUID subject, @NotNull JobId job,
                                               @NotNull JobActor actor, @Nullable String reason);

    /** Revoke {@code job} from {@code subject}. Works whether or not the player is online. */
    @NotNull CompletableFuture<JobResult> fire(@NotNull UUID subject, @NotNull JobId job,
                                               @NotNull JobActor actor, @Nullable String reason);

    /**
     * Whether {@code subject} holds {@code job}, directly or by inheritance.
     * Authoritative — it loads the user from LuckPerms storage if they are offline.
     */
    @NotNull CompletableFuture<Boolean> holds(@NotNull UUID subject, @NotNull JobId job);

    /** Every job {@code subject} holds, ordered by type then configuration order. */
    @NotNull CompletableFuture<List<HeldJob>> jobsOf(@NotNull UUID subject);

    /** As {@link #jobsOf(UUID)}, restricted to one type key. */
    @NotNull CompletableFuture<List<HeldJob>> jobsOf(@NotNull UUID subject, @NotNull String typeKey);

    /**
     * Cache-only, synchronous, non-blocking membership check.
     *
     * <p>A fast path for a player you already know is online — gating a block-break
     * handler, say. It reads only LuckPerms' in-memory cache and therefore
     * <strong>returns false for an offline or uncached player even when they do hold
     * the job</strong>. Use {@link #holds(UUID, JobId)} whenever a false negative
     * would be wrong.</p>
     */
    boolean holdsCached(@NotNull UUID subject, @NotNull JobId job);
}
