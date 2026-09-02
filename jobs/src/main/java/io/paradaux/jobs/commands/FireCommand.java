package io.paradaux.jobs.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.annotations.Arg;
import io.paradaux.hibernia.framework.commander.annotations.Async;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.GreedyArg;
import io.paradaux.hibernia.framework.commander.annotations.Permission;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.jobs.commands.resolvers.JobArg;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

/** {@code /fire} — a convenience alias for {@code /jobs fire}. Console-capable. */
@Singleton
@Command({"fire"})
public final class FireCommand implements CommandHandler {

    private final JobActions actions;

    @Inject
    public FireCommand(JobActions actions) {
        this.actions = actions;
    }

    @Route("<player> <job>")
    @Permission("jobs.fire")
    @Async
    @Description("Remove a player from a job, licence or qualification")
    public void fire(@Sender CommandSender sender,
                     @Arg("player") OfflinePlayer target,
                     @Arg("job") JobArg job) {
        actions.fire(sender, target, job, null);
    }

    @Route("<player> <job> <reason>")
    @Permission("jobs.fire")
    @Async
    @Description("Remove a player from a job, recording a reason")
    public void fireWithReason(@Sender CommandSender sender,
                               @Arg("player") OfflinePlayer target,
                               @Arg("job") JobArg job,
                               @GreedyArg("reason") String reason) {
        actions.fire(sender, target, job, reason);
    }
}
