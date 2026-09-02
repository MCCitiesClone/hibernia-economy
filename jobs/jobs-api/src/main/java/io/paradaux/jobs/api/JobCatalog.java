package io.paradaux.jobs.api;

import io.paradaux.jobs.api.model.JobDefinition;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.api.model.JobType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * A read-only view of what jobs, licences and qualifications are configured.
 *
 * <p>Every method is a plain in-memory snapshot read and is safe to call from any
 * thread, including the main thread — which is why the catalogue is synchronous
 * while membership reads and writes on {@link JobsApi} are not.</p>
 *
 * <p>A snapshot is immutable. A configuration reload publishes a new one, so hold a
 * reference only for the duration of one operation.</p>
 */
public interface JobCatalog {

    /** All configured types, ordered by their configured {@code order}. */
    @NotNull List<JobType> types();

    Optional<JobType> type(@NotNull String typeKey);

    Optional<JobDefinition> job(@NotNull JobId id);

    /**
     * Resolve a user-supplied token: either a fully-qualified {@code type/job}, or a
     * bare job key that occurs in exactly one type. A bare key present in several
     * types is ambiguous and yields empty — callers should report
     * {@link Outcome#AMBIGUOUS_JOB} rather than guessing.
     */
    Optional<JobId> parse(@NotNull String token);

    /** Reverse lookup: which job, if any, a LuckPerms group represents. */
    Optional<JobId> byGroup(@NotNull String luckPermsGroup);

    /** Every configured job across all types. */
    @NotNull List<JobId> jobs();

    @NotNull List<JobId> jobsOfType(@NotNull String typeKey);
}
