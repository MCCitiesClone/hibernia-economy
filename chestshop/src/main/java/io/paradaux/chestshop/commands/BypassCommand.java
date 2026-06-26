package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.AdminBypass;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.configuration.Messages;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import org.bukkit.entity.Player;

/**
 * {@code /chestshop bypass} — toggle your own admin bypass. Its own handler (not a
 * route on {@link ChestShopCommand}) so the class-level {@code @Permission} filters
 * the subcommand out of tab-completion for non-admins — the tree builder only
 * applies the <em>class</em> permission to Brigadier's {@code requires} predicate;
 * a route-level one would still execute-gate but would leak into completion.
 */
@Command({"chestshop", "cs"})
@io.paradaux.hibernia.framework.commander.annotations.Permission(Permission.Node.ADMIN)
public class BypassCommand implements CommandHandler {

    @Route("bypass")
    @Description("Toggle your own admin bypass — off lets you play as a normal customer")
    public void bypass(@Sender Player player) {
        if (AdminBypass.toggle(player)) {
            Messages.BYPASS_OFF.sendWithPrefix(player);
        } else {
            Messages.BYPASS_ON.sendWithPrefix(player);
        }
    }
}
