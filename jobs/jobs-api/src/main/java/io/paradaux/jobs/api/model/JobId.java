package io.paradaux.jobs.api.model;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Identifies one job, licence or qualification as a {@code type/job} pair — for
 * example {@code trades/electrician} or {@code licenses/firearms}.
 *
 * <p>Both halves are the <em>configuration keys</em> from {@code jobs.yml}, not
 * display names, and are normalised to lower case so comparison and map lookup are
 * case-insensitive throughout. A display name may be renamed freely without
 * invalidating stored history or a {@code can-manage} selector.</p>
 */
public record JobId(@NotNull String type, @NotNull String job) {

    public JobId {
        type = normalise(type, "type");
        job = normalise(job, "job");
    }

    public static JobId of(@NotNull String type, @NotNull String job) {
        return new JobId(type, job);
    }

    /**
     * Parse a fully-qualified {@code type/job} token.
     *
     * <p>Returns empty for anything that is not exactly two non-blank segments, so a
     * bare job key like {@code electrician} is <em>not</em> accepted here. Resolving a
     * bare key needs the configured catalogue to know whether it is unambiguous —
     * use {@link io.paradaux.jobs.api.JobCatalog#parse(String)} for that.</p>
     */
    public static Optional<JobId> parseQualified(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int slash = token.indexOf('/');
        if (slash <= 0 || slash == token.length() - 1) {
            return Optional.empty();
        }
        String type = token.substring(0, slash).trim();
        String job = token.substring(slash + 1).trim();
        if (type.isEmpty() || job.isEmpty() || job.indexOf('/') >= 0) {
            return Optional.empty();
        }
        return Optional.of(new JobId(type, job));
    }

    /** The canonical {@code type/job} form, as accepted by commands and the API. */
    public @NotNull String qualified() {
        return type + "/" + job;
    }

    @Override
    public String toString() {
        return qualified();
    }

    private static String normalise(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Job " + what + " must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
