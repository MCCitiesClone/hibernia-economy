package io.paradaux.jobs.commands;

import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.JobResult;
import io.paradaux.jobs.api.model.JobDefinition;
import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.services.JobSnapshot;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared helpers for the job command handlers.
 *
 * <p>Console is a first-class actor throughout — it must be, because another plugin
 * or an operator script needs to hire anyone into anything. The pattern follows
 * Treasury's {@code GovCommand.isAllowed}: a non-player sender passes every check,
 * and the service-layer hierarchy is bypassed by the actor being privileged.</p>
 */
public final class CommandSupport {

    private CommandSupport() {
    }

    /**
     * Bukkit-permission gate that always passes for the console and RCON.
     *
     * <p>The node governs only whether a sender may <em>attempt</em> the command; the
     * real authority check is the can-manage hierarchy in the service.</p>
     */
    public static boolean isAllowed(CommandSender sender, String node) {
        return !(sender instanceof Player) || sender.hasPermission(node);
    }

    /**
     * Build the actor for a sender. A player is privileged only if they hold the
     * configured admin permission; anything else — console, RCON, a command block —
     * is inherently privileged.
     */
    public static JobActor actorOf(CommandSender sender, JobSnapshot snapshot) {
        if (sender instanceof Player player) {
            return JobActor.player(player.getUniqueId(), player.getName(),
                    player.hasPermission(snapshot.adminPermission()));
        }
        return JobActor.console();
    }

    /**
     * The sender as a player, or empty for the console.
     *
     * <p>Routes whose subject is implicit ({@code /jobs} with no argument,
     * {@code /jobs quit}) need this: the console holds no jobs, so it must name a
     * target instead of being silently treated as nobody.
     */
    public static Optional<UUID> selfUuid(CommandSender sender) {
        return sender instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }

    /** Tell the console it must name a player, pointing at the form that works. */
    public static void requireTarget(Message message, CommandSender sender, String key) {
        message.send(sender, key);
    }

    /**
     * Resolve a typed job token, reporting the specific reason it failed.
     *
     * @return the job, or empty after a message has already been sent.
     */
    public static Optional<JobId> resolveJob(Message message, CommandSender sender,
                                             JobSnapshot snapshot, String token) {
        Optional<JobId> resolved = snapshot.parse(token);
        if (resolved.isPresent()) {
            return resolved;
        }
        // Distinguishing these two matters: "ambiguous" tells the player to qualify
        // the name, "unknown" tells them they got it wrong.
        if (snapshot.isAmbiguousBareKey(token)) {
            message.send(sender, "jobs.error.ambiguous-job", "job", token);
        } else {
            message.send(sender, "jobs.error.unknown-job", "job", token);
        }
        return Optional.empty();
    }

    /**
     * Render the outcome of a hire, fire or quit to the actor, and notify the target
     * when they are online and it was someone else's doing.
     */
    public static void reportHire(Message message, CommandSender sender, JobResult result,
                                  JobSnapshot snapshot, String targetName, UUID target) {
        String job = displayName(snapshot, result.job());
        switch (result.outcome()) {
            case SUCCESS -> {
                message.send(sender, "jobs.hire.sender", "target", targetName, "job", job);
                notify(message, target, "jobs.hire.target", job, senderName(sender));
            }
            case ALREADY_HELD -> message.send(sender, "jobs.hire.already",
                    "target", targetName, "job", job);
            case EXTERNALLY_MANAGED -> message.send(sender, "jobs.error.externally-managed",
                    "job", job);
            default -> reportFailure(message, sender, result, job);
        }
    }

    public static void reportFire(Message message, CommandSender sender, JobResult result,
                                  JobSnapshot snapshot, String targetName, UUID target) {
        String job = displayName(snapshot, result.job());
        switch (result.outcome()) {
            case SUCCESS -> {
                message.send(sender, "jobs.fire.sender", "target", targetName, "job", job);
                result.detailOptional().ifPresent(detail ->
                        message.send(sender, "jobs.fire.still-inherited",
                                "target", targetName, "job", job));
                notify(message, target, "jobs.fire.target", job, senderName(sender));
            }
            case NOT_HELD -> message.send(sender, "jobs.fire.not-held",
                    "target", targetName, "job", job);
            case INHERITED_NOT_DIRECT -> message.send(sender, "jobs.error.inherited",
                    "target", targetName, "job", job);
            default -> reportFailure(message, sender, result, job);
        }
    }

    public static void reportQuit(Message message, CommandSender sender, JobResult result,
                                  JobSnapshot snapshot) {
        String job = displayName(snapshot, result.job());
        switch (result.outcome()) {
            case SUCCESS -> message.send(sender, "jobs.quit.success", "job", job);
            case NOT_HELD -> message.send(sender, "jobs.quit.not-held", "job", job);
            case INHERITED_NOT_DIRECT -> message.send(sender, "jobs.quit.inherited", "job", job);
            default -> reportFailure(message, sender, result, job);
        }
    }

    private static void reportFailure(Message message, CommandSender sender,
                                      JobResult result, String job) {
        switch (result.outcome()) {
            case UNKNOWN_JOB -> message.send(sender, "jobs.error.unknown-job", "job", job);
            case NOT_AUTHORISED -> message.send(sender, "jobs.error.not-authorised", "job", job);
            case PERMISSIONS_UNAVAILABLE ->
                    message.send(sender, "jobs.error.permissions-unavailable");
            default -> message.send(sender, "jobs.error.backend",
                    "error", result.detailOptional().orElse("unknown error"));
        }
    }

    /** Tell the affected player, if they are online. */
    private static void notify(Message message, UUID target, String key, String job, String actor) {
        if (target != null) {
            // Message.send(UUID, ...) is a no-op for an offline player.
            message.send(target, key, "job", job, "sender", actor);
        }
    }

    private static String senderName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : "the console";
    }

    private static String displayName(JobSnapshot snapshot, JobId id) {
        if (id == null) {
            return "?";
        }
        return snapshot.job(id).map(JobDefinition::displayName).orElseGet(id::qualified);
    }
}
