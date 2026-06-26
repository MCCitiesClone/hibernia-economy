package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.events.ChestShopReloadEvent;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * @author Acrobot
 */
@Command({"csVersion", "chestshop"})
@io.paradaux.hibernia.framework.commander.annotations.Permission(Permission.Node.ADMIN)
public class Version implements CommandHandler {

    @Route("")
    public void version(@Sender CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + ChestShop.getPluginName() + "'s version is: " + ChatColor.GREEN + ChestShop.getVersion());
    }

    @Route("reload")
    public void reload(@Sender CommandSender sender) {
        ChestShop.callEvent(new ChestShopReloadEvent(sender));
        sender.sendMessage(ChatColor.DARK_GREEN + "The config was reloaded.");
    }
}
