package io.paradaux.jobs.event;

import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.model.JobId;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired after a player has left a job of their own accord.
 *
 * <p>Quitting is never blocked and never requires authority, so this event is purely
 * informational — there is no cancellation. A plugin that owns a job type may
 * re-grant it on its next sync; that would raise {@link PlayerHiredEvent}.</p>
 */
public class PlayerQuitJobEvent extends JobChangeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerQuitJobEvent(@NotNull UUID subject, @NotNull JobId job, @NotNull JobActor actor) {
        super(subject, job, actor, null);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
