package io.paradaux.jobs.event;

import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.model.JobId;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Common shape of the job membership events.
 *
 * <p>These fire only when a membership actually changed — a no-op hire of a job the
 * player already holds produces no event, matching the audit log, which records
 * transitions rather than attempts.</p>
 *
 * <p><b>Threading:</b> dispatched on the main thread after the LuckPerms write has
 * completed, so handlers may touch Bukkit state freely. Note the consequence: the
 * event arrives slightly after the permission change is already live.</p>
 */
public abstract class JobChangeEvent extends Event {

    private final UUID subject;
    private final JobId job;
    private final JobActor actor;
    private final String reason;

    protected JobChangeEvent(@NotNull UUID subject, @NotNull JobId job,
                             @NotNull JobActor actor, @Nullable String reason) {
        super(false /* synchronous: fired on the main thread */);
        this.subject = subject;
        this.job = job;
        this.actor = actor;
        this.reason = reason;
    }

    /** The player whose membership changed. */
    public @NotNull UUID getSubject() {
        return subject;
    }

    public @NotNull JobId getJob() {
        return job;
    }

    /** Who caused the change — a player, the console, another plugin, or the reconciler. */
    public @NotNull JobActor getActor() {
        return actor;
    }

    public @Nullable String getReason() {
        return reason;
    }
}
