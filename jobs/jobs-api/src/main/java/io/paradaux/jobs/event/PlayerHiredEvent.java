package io.paradaux.jobs.event;

import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.model.JobId;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Fired after a player has been granted a job, licence or qualification. */
public class PlayerHiredEvent extends JobChangeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerHiredEvent(@NotNull UUID subject, @NotNull JobId job,
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
