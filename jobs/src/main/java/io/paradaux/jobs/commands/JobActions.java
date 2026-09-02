package io.paradaux.jobs.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.JobResult;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.commands.resolvers.JobArg;
import io.paradaux.jobs.services.JobListRenderer;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.services.JobService;
import io.paradaux.jobs.services.JobSnapshot;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.UUID;

/**
 * The bodies behind every job command.
 *
 * <p>{@code /jobs hire} and the bare {@code /hire} must behave identically, and the
 * three listing roots differ only by a type filter, so the actual work lives here and
 * the handler classes are thin. They have to be separate classes at all only because
 * {@code @Command} is a class-level compile-time literal and the framework reads
 * routes from {@code getDeclaredMethods()}, so routes cannot be inherited from a
 * shared base.</p>
 */
@Singleton
public final class JobActions {

    private final Message message;
    private final JobService jobs;
    private final JobRegistry registry;
    private final JobListRenderer renderer;

    @Inject
    public JobActions(Message message, JobService jobs, JobRegistry registry,
                      JobListRenderer renderer) {
        this.message = message;
        this.jobs = jobs;
        this.registry = registry;
        this.renderer = renderer;
    }

    // ---- listing ----

    /** {@code /jobs}, {@code /licenses}, {@code /qualifications} with no target. */
    public void listSelf(CommandSender sender, JobListRenderer.Filter filter) {
        Optional<UUID> self = CommandSupport.selfUuid(sender);
        if (self.isEmpty()) {
            // The console holds no jobs; point it at the form that does work.
            message.send(sender, "jobs.error.needs-target");
            return;
        }
        renderer.render(sender, self.get(), sender.getName(), filter);
    }

    /** The same three commands with an explicit target — console-capable. */
    public void listOther(CommandSender sender, OfflinePlayer target, JobListRenderer.Filter filter) {
        renderer.render(sender, target.getUniqueId(), nameOf(target), filter);
    }

    /** {@code /jobs licenses} and friends, resolving the type key from configuration. */
    public void listByCommand(CommandSender sender, OfflinePlayer target, String listingCommand) {
        Optional<String> typeKey = renderer.listingType(listingCommand);
        if (typeKey.isEmpty()) {
            message.send(sender, "jobs.list.type-unconfigured");
            return;
        }
        JobListRenderer.Filter filter = JobListRenderer.Filter.only(typeKey.get());
        if (target == null) {
            listSelf(sender, filter);
        } else {
            listOther(sender, target, filter);
        }
    }

    /** {@code /jobs type <type> [player]}. */
    public void listByType(CommandSender sender, String typeToken, OfflinePlayer target) {
        JobSnapshot snapshot = registry.snapshot();
        if (snapshot.type(typeToken).isEmpty()) {
            message.send(sender, "jobs.error.unknown-type", "type", typeToken);
            return;
        }
        JobListRenderer.Filter filter = JobListRenderer.Filter.only(typeToken);
        if (target == null) {
            listSelf(sender, filter);
        } else {
            listOther(sender, target, filter);
        }
    }

    // ---- mutations ----

    public void hire(CommandSender sender, OfflinePlayer target, JobArg jobArg, String reason) {
        JobSnapshot snapshot = registry.snapshot();
        Optional<JobId> job = CommandSupport.resolveJob(message, sender, snapshot, jobArg.value());
        if (job.isEmpty()) {
            return;
        }
        JobActor actor = CommandSupport.actorOf(sender, snapshot);
        JobResult result = jobs.hire(target.getUniqueId(), job.get(), actor, reason).join();
        CommandSupport.reportHire(message, sender, result, snapshot, nameOf(target),
                target.getUniqueId());
    }

    public void fire(CommandSender sender, OfflinePlayer target, JobArg jobArg, String reason) {
        JobSnapshot snapshot = registry.snapshot();
        Optional<JobId> job = CommandSupport.resolveJob(message, sender, snapshot, jobArg.value());
        if (job.isEmpty()) {
            return;
        }
        JobActor actor = CommandSupport.actorOf(sender, snapshot);
        JobResult result = jobs.fire(target.getUniqueId(), job.get(), actor, reason).join();
        CommandSupport.reportFire(message, sender, result, snapshot, nameOf(target),
                target.getUniqueId());
    }

    public void quit(CommandSender sender, JobArg jobArg) {
        Optional<UUID> self = CommandSupport.selfUuid(sender);
        if (self.isEmpty()) {
            // Console has no jobs of its own — it fires people instead.
            message.send(sender, "jobs.error.player-only");
            return;
        }
        JobSnapshot snapshot = registry.snapshot();
        Optional<JobId> job = CommandSupport.resolveJob(message, sender, snapshot, jobArg.value());
        if (job.isEmpty()) {
            return;
        }
        JobResult result = jobs.quit(self.get(), job.get()).join();
        CommandSupport.reportQuit(message, sender, result, snapshot);
    }

    private static String nameOf(OfflinePlayer player) {
        String name = player.getName();
        return name != null ? name : player.getUniqueId().toString();
    }
}
