package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.events.ShopInfoEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.uBlock;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * @author Phoenix616
 */
@Command({"shopinfo", "sinfo", "shop"})
@io.paradaux.hibernia.framework.commander.annotations.Permission(Permission.Node.SHOPINFO)
public class ShopInfo implements CommandHandler {

    @Route("")
    public void shopInfo(@Sender CommandSender sender) {
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
                    ChestShop.message().send(sender, "chestshop.NO_SHOP_FOUND");
                }
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Command must be run by a player!");
        }
    }
}
