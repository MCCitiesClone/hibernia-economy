package io.paradaux.jobs.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.annotations.Arg;
import io.paradaux.hibernia.framework.commander.annotations.Async;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.GreedyArg;
import io.paradaux.hibernia.framework.commander.annotations.OptionalArg;
import io.paradaux.hibernia.framework.commander.annotations.Permission;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.jobs.commands.resolvers.JobArg;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

/**
 * {@code /hire} — a convenience alias for {@code /jobs hire}.
 *
 * <p>A bare root like this is a name another plugin may already own, and whichever
 * registers first wins; the canonical {@code /jobs hire} always works. Jobs logs a
 * warning at startup if a bare root resolved to someone else.</p>
 *
 * <p>The sender is a {@link CommandSender}, not a {@code Player}, so the console and
 * RCON can hire — required because another plugin or an operator script must be able
 * to grant any job.</p>
 */
@Singleton
@Command({"hire"})
public final class HireCommand implements CommandHandler {

    private final JobActions actions;

    @Inject
    public HireCommand(JobActions actions) {
        this.actions = actions;
    }

    @Route("<player> <job>")
    @Permission("jobs.hire")
    @Async
    @Description("Hire a player into a job, licence or qualification")
    public void hire(@Sender CommandSender sender,
                     @Arg("player") OfflinePlayer target,
                     @Arg("job") JobArg job) {
        actions.hire(sender, target, job, null);
    }

    @Route("<player> <job> <reason>")
    @Permission("jobs.hire")
    @Async
    @Description("Hire a player into a job, recording a reason")
    public void hireWithReason(@Sender CommandSender sender,
                               @Arg("player") OfflinePlayer target,
                               @Arg("job") JobArg job,
                               @GreedyArg("reason") String reason) {
        actions.hire(sender, target, job, reason);
    }
}
