package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.services.ItemInfoLines;
import io.paradaux.chestshop.utils.ItemUtil;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.GreedyArg;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.logging.Level;

/**
 * @author Acrobot
 */
@Command({"iteminfo", "iinfo"})
@io.paradaux.hibernia.framework.commander.annotations.Permission(Permission.Node.ITEMINFO)
public class ItemInfo implements CommandHandler {

    @Route("")
    public void heldItem(@Sender CommandSender sender) {
        if (!(sender instanceof HumanEntity)) {
            return;
        }

        ItemStack item = ((HumanEntity) sender).getItemInHand();
        showItemInfo(sender, item);
    }

    @Route("<item>")
    public void parsedItem(@Sender CommandSender sender,
                           @GreedyArg(value = "item", sanitize = false) String item) {
        showItemInfo(sender, ChestShop.items().parse(item));
    }

    private static void showItemInfo(CommandSender sender, ItemStack item) {
        if (MaterialUtil.isEmpty(item)) {
            return;
        }

        ChestShop.message().send(sender, "chestshop.iteminfo", "prefix", "");
        if (!ChestShop.info().sendItemName(sender, item, "chestshop.iteminfo_fullname")) return;

        try {
            ChestShop.message().send(sender, "chestshop.iteminfo_shopname", "prefix", "", "item", ItemUtil.getSignName(item));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Error while generating shop sign name. Please contact an admin or take a look at the console/log!");
            ChestShop.getPlugin().getLogger().log(Level.SEVERE, "Error while generating shop sign item name", e);
            return;
        }

        ItemInfoLines lines = ChestShop.info().collectItemInfo(sender, item);
        for (Map.Entry<String, Component> entry : lines.getMessages()) {
            sender.sendMessage(entry.getValue());
        }
    }
}
