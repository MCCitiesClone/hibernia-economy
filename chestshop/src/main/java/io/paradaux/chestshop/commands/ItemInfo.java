package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Messages;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.ItemInfoEvent;
import io.paradaux.chestshop.events.ItemParseEvent;
import io.paradaux.chestshop.utils.ItemUtil;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.GreedyArg;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.logging.Level;

import static io.paradaux.chestshop.configuration.Messages.iteminfo;
import static io.paradaux.chestshop.configuration.Messages.iteminfo_shopname;

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
        ItemParseEvent parseEvent = new ItemParseEvent(item);
        Bukkit.getPluginManager().callEvent(parseEvent);
        showItemInfo(sender, parseEvent.getItem());
    }

    private static void showItemInfo(CommandSender sender, ItemStack item) {
        if (MaterialUtil.isEmpty(item)) {
            return;
        }

        iteminfo.send(sender);
        if (!sendItemName(sender, item, Messages.iteminfo_fullname)) return;

        try {
            iteminfo_shopname.send(sender, "item", ItemUtil.getSignName(item));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Error while generating shop sign name. Please contact an admin or take a look at the console/log!");
            ChestShop.getPlugin().getLogger().log(Level.SEVERE, "Error while generating shop sign item name", e);
            return;
        }

        ItemInfoEvent event = ChestShop.callEvent(new ItemInfoEvent(sender, item));
        for (Map.Entry<Messages.Message, String[]> entry : event.getMessages()) {
            entry.getKey().send(sender, entry.getValue());
        }
    }

    public static boolean sendItemName(CommandSender sender, ItemStack item, Messages.Message message) {
        try {
            Map<String, String> replacementMap = ImmutableMap.of("item", ItemUtil.getName(item));
            if (!Properties.SHOWITEM_MESSAGE || !(sender instanceof Player)
                    || !MaterialUtil.Show.sendMessage((Player) sender, sender.getName(), message, false, new ItemStack[]{item}, replacementMap)) {
                message.send(sender, replacementMap);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Error while generating full name. Please contact an admin or take a look at the console/log!");
            ChestShop.getPlugin().getLogger().log(Level.SEVERE, "Error while generating full item name", e);
            return false;
        }
        return true;
    }
}
