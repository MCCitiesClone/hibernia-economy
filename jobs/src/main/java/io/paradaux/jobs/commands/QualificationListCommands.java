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
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

/** {@code /qualifications} — the qualifications a player holds. See {@link LicenseListCommands}. */
@Singleton
@Command({"qualifications", "qualification", "quals"})
public final class QualificationListCommands implements CommandHandler {

    public static final String LISTING = "qualifications";

    private final JobActions actions;

    @Inject
    public QualificationListCommands(JobActions actions) {
        this.actions = actions;
    }

    @Route("")
    @Permission("jobs.qualifications")
    @Async
    @Description("List the qualifications you hold")
    public void self(@Sender CommandSender sender) {
        actions.listByCommand(sender, null, LISTING);
    }

    @Route("<player>")
    @Permission("jobs.list.other")
    @Async
    @Description("List the qualifications another player holds")
    public void other(@Sender CommandSender sender, @Arg("player") OfflinePlayer target) {
        actions.listByCommand(sender, target, LISTING);
    }
}
