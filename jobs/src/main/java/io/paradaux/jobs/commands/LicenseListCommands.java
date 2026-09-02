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

/**
 * {@code /licenses} — the licences a player holds.
 *
 * <p>Which configured type this shows comes from {@code listing-commands.licenses} in
 * {@code jobs.yml}, so a type can be renamed or re-pointed without touching code. The
 * rendering itself is shared with {@code /jobs}; only the filter differs.</p>
 */
@Singleton
@Command({"licenses", "license", "licences", "licence"})
public final class LicenseListCommands implements CommandHandler {

    /** The {@code listing-commands} key this root reads. */
    public static final String LISTING = "licenses";

    private final JobActions actions;

    @Inject
    public LicenseListCommands(JobActions actions) {
        this.actions = actions;
    }

    @Route("")
    @Permission("jobs.licenses")
    @Async
    @Description("List the licences you hold")
    public void self(@Sender CommandSender sender) {
        actions.listByCommand(sender, null, LISTING);
    }

    @Route("<player>")
    @Permission("jobs.list.other")
    @Async
    @Description("List the licences another player holds")
    public void other(@Sender CommandSender sender, @Arg("player") OfflinePlayer target) {
        actions.listByCommand(sender, target, LISTING);
    }
}
