package io.paradaux.chestshop.commands;
import io.paradaux.chestshop.utils.Colours;

import com.google.inject.Inject;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Description;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import org.bukkit.command.CommandSender;

/**
 * @author Acrobot
 */
@Command({"chestshop", "cs"})
@io.paradaux.hibernia.framework.commander.annotations.Permission("ChestShop.admin")
public class VersionCommand implements CommandHandler {

    private final ItemService items;

    @Inject
    public VersionCommand(ItemService items) {
        this.items = items;
    }

    @Route("version")
    @Description("Show the ChestShop plugin version")
    public void version(@Sender CommandSender sender) {
        sender.sendMessage(Colours.GRAY + ChestShop.getPluginName() + "'s version is: " + Colours.GREEN + ChestShop.getVersion());
    }

    @Route("reload")
    @Description("Reload the ChestShop configuration")
    public void reload(@Sender CommandSender sender) {
        // Was the ChestShopReloadEvent fan-out: reload the config and the item
        // aliases (ItemAliasModule). Both run directly now.
        ChestShop.getPlugin().loadConfig();
        items.reloadAliases();
        sender.sendMessage(Colours.DARK_GREEN + "The config was reloaded.");
    }
}
