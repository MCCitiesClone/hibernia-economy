package io.paradaux.jobs.event;

import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.model.JobId;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired after a player has been removed from a job by someone else.
 *
 * <p>A player leaving of their own accord raises {@link PlayerQuitJobEvent} instead,
 * so a handler can distinguish dismissal from resignation without inspecting the
 * actor.</p>
 */
public class PlayerFiredEvent extends JobChangeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerFiredEvent(@NotNull UUID subject, @NotNull JobId job,
                            @NotNull JobActor actor, @Nullable String reason) {
        super(subject, job, actor, reason);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
