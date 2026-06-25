package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.utils.StringUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Messages;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.ItemInfoEvent;
import io.paradaux.chestshop.events.ItemParseEvent;
import io.paradaux.chestshop.utils.ItemUtil;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
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
public class ItemInfo implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        ItemStack item;

        if (args.length == 0) {
            if (!(sender instanceof HumanEntity)) {
                return false;
            }

            item = ((HumanEntity) sender).getItemInHand();
        } else {
            ItemParseEvent parseEvent = new ItemParseEvent(StringUtil.joinArray(args));
            Bukkit.getPluginManager().callEvent(parseEvent);
            item = parseEvent.getItem();
        }

        if (MaterialUtil.isEmpty(item)) {
            return false;
        }

        iteminfo.send(sender);
        if (!sendItemName(sender, item, Messages.iteminfo_fullname)) return true;

        try {
            iteminfo_shopname.send(sender, "item", ItemUtil.getSignName(item));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Error while generating shop sign name. Please contact an admin or take a look at the console/log!");
            ChestShop.getPlugin().getLogger().log(Level.SEVERE, "Error while generating shop sign item name", e);
            return true;
        }

        ItemInfoEvent event = ChestShop.callEvent(new ItemInfoEvent(sender, item));
        for (Map.Entry<Messages.Message, String[]> entry : event.getMessages()) {
            entry.getKey().send(sender, entry.getValue());
        }

        return true;
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
