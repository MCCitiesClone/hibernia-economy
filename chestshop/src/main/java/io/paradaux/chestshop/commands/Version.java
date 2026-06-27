package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Permission;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * @author Acrobot
 */
@Command({"chestshop", "cs"})
@io.paradaux.hibernia.framework.commander.annotations.Permission(Permission.Node.ADMIN)
public class Version implements CommandHandler {

    @Route("version")
    @Description("Show the ChestShop plugin version")
    public void version(@Sender CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + ChestShop.getPluginName() + "'s version is: " + ChatColor.GREEN + ChestShop.getVersion());
    }

    @Route("reload")
    @Description("Reload the ChestShop configuration")
    public void reload(@Sender CommandSender sender) {
        // Was the ChestShopReloadEvent fan-out: reload config (plugins/ChestShop) and the
        // item aliases (ItemAliasModule). Both run directly now.
        ChestShop.getPlugin().loadConfig();
        ChestShop.items().reloadAliases();
        sender.sendMessage(ChatColor.DARK_GREEN + "The config was reloaded.");
    }
}
