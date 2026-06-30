package io.paradaux.chestshop.commands;

import com.google.inject.Inject;
import io.paradaux.chestshop.services.InfoService;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.ShopBlockUtil;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.i18n.Message;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * @author Phoenix616
 */
@Command({"shopinfo", "sinfo", "shop"})
@io.paradaux.hibernia.framework.commander.annotations.Permission("ChestShop.shopinfo")
public class ShopInfo implements CommandHandler {

    private final InfoService info;
    private final Message message;
    private final ChestShopSign chestShopSign;
    private final ShopBlockUtil shopBlockUtil;

    @Inject
    public ShopInfo(InfoService info, Message message, ChestShopSign chestShopSign, ShopBlockUtil shopBlockUtil) {
        this.info = info;
        this.message = message;
        this.chestShopSign = chestShopSign;
        this.shopBlockUtil = shopBlockUtil;
    }

    @Route("")
    public void shopInfo(@Sender CommandSender sender) {
        if (sender instanceof Player) {
            Block target = ((Player) sender).getTargetBlockExact(5);
            if (target != null) {
                Sign sign = null;
                if (chestShopSign.isValid(target)) {
                    sign = (Sign) target.getState();
                } else if (shopBlockUtil.couldBeShopContainer(target)) {
                    sign = shopBlockUtil.getConnectedSign(target);
                }

                if (sign != null) {
                    info.showShopInfo((Player) sender, sign);
                } else {
                    message.send(sender, "chestshop.NO_SHOP_FOUND");
                }
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Command must be run by a player!");
        }
    }
}
