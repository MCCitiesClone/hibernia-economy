package io.paradaux.jobs.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.event.PlayerFiredEvent;
import io.paradaux.jobs.event.PlayerHiredEvent;
import io.paradaux.jobs.event.PlayerQuitJobEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fires the public job events, on the main thread.
 *
 * <p>Job changes complete on the permission backend's executor, but handlers will
 * want to touch Bukkit state — messages, scoreboards, inventories — so the hop back
 * happens here rather than being every listener's problem. The consequence, which the
 * events document, is that a handler observes the change slightly after it is already
 * live in LuckPerms.</p>
 *
 * <p>Events are fired only for real transitions. A no-op hire produces none, matching
 * the audit log.</p>
 */
@Singleton
public class JobEventPublisher {

    private final JavaPlugin plugin;

    @Inject
    public JobEventPublisher(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void hired(UUID subject, JobId job, JobActor actor, String reason) {
        fire(new PlayerHiredEvent(subject, job, actor, reason));
    }

    public void fired(UUID subject, JobId job, JobActor actor, String reason) {
        fire(new PlayerFiredEvent(subject, job, actor, reason));
    }

    public void quit(UUID subject, JobId job, JobActor actor) {
        fire(new PlayerQuitJobEvent(subject, job, actor));
    }

    private void fire(Event event) {
        run(server -> server.getPluginManager().callEvent(event));
    }

    /** Hop to the main thread unless we are already on it. */
    private void run(Consumer<org.bukkit.Server> action) {
        try {
            if (Bukkit.isPrimaryThread()) {
                action.accept(plugin.getServer());
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> action.accept(plugin.getServer()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            // The scheduler rejects tasks once the plugin is disabling. A missed
            // notification during shutdown is not worth failing the operation that
            // already succeeded.
            plugin.getLogger().fine("Skipped a job event during shutdown: " + e.getMessage());
        }
    }
}
