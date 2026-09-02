package io.paradaux.jobs.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.annotations.Arg;
import io.paradaux.hibernia.framework.commander.annotations.Async;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.Permission;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.jobs.commands.resolvers.JobArg;
import org.bukkit.command.CommandSender;

/**
 * {@code /quit} — a convenience alias for {@code /jobs quit}, with {@code /resign}.
 *
 * <p>Quitting is never gated on the hierarchy and never confirmed: a player may leave
 * any job immediately, so nobody can be trapped in one. If a plugin owns the job type
 * it may re-grant it on its next sync, which the success message notes.</p>
 */
@Singleton
@Command({"quit", "resign"})
public final class QuitCommand implements CommandHandler {

    private final JobActions actions;

    @Inject
    public QuitCommand(JobActions actions) {
        this.actions = actions;
    }

    @Route("<job>")
    @Permission("jobs.quit")
    @Async
    @Description("Leave a job you hold")
    public void quit(@Sender CommandSender sender, @Arg("job") JobArg job) {
        actions.quit(sender, job);
    }
}
