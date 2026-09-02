package io.paradaux.jobs.api;

import io.paradaux.jobs.api.model.JobId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The outcome of a hire, fire or quit, plus enough context to render a message.
 *
 * @param outcome what happened
 * @param job     the job acted on, absent when the token could not be resolved
 * @param subject the player acted on
 * @param detail  optional extra context (an error message, or a note such as the
 *                group still being inherited after a successful direct removal)
 */
public record JobResult(@NotNull Outcome outcome,
                        @Nullable JobId job,
                        @Nullable UUID subject,
                        @Nullable String detail) {

    public static JobResult of(@NotNull Outcome outcome, @Nullable JobId job, @Nullable UUID subject) {
        return new JobResult(outcome, job, subject, null);
    }

    public static JobResult of(@NotNull Outcome outcome, @Nullable JobId job,
                               @Nullable UUID subject, @Nullable String detail) {
        return new JobResult(outcome, job, subject, detail);
    }

    public boolean successful() {
        return outcome.successful();
    }

    public Optional<JobId> jobOptional() {
        return Optional.ofNullable(job);
    }

    public Optional<String> detailOptional() {
        return Optional.ofNullable(detail);
    }
}
