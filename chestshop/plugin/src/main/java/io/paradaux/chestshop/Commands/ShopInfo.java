package io.paradaux.chestshop.Commands;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Configuration.Messages;
import io.paradaux.chestshop.Events.ShopInfoEvent;
import io.paradaux.chestshop.Signs.ChestShopSign;
import io.paradaux.chestshop.Utils.uBlock;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * @author Phoenix616
 */
public class ShopInfo implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player) {
            Block target = ((Player) sender).getTargetBlockExact(5);
            if (target != null) {
                Sign sign = null;
                if (ChestShopSign.isValid(target)) {
                    sign = (Sign) target.getState();
                } else if (uBlock.couldBeShopContainer(target)) {
                    sign = uBlock.getConnectedSign(target);
                }

                if (sign != null) {
                    ShopInfoEvent event = new ShopInfoEvent((Player) sender, sign);
                    ChestShop.callEvent(event);
                } else {
                    Messages.NO_SHOP_FOUND.sendWithPrefix(sender);
                }
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Command must be run by a player!");
        }
        return true;
    }
}
