package io.paradaux.chestshop.commands;

import io.paradaux.chestshop.services.ShopBlockService;
import com.google.inject.Inject;
import io.paradaux.chestshop.services.InfoService;
import io.paradaux.chestshop.services.ChestShopSign;
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
public class ShopInfoCommand implements CommandHandler {

    private final InfoService info;
    private final Message message;
    private final ChestShopSign chestShopSign;
    private final ShopBlockService shopBlockService;

    @Inject
    public ShopInfoCommand(InfoService info, Message message, ChestShopSign chestShopSign, ShopBlockService shopBlockService) {
        this.info = info;
        this.message = message;
        this.chestShopSign = chestShopSign;
        this.shopBlockService = shopBlockService;
    }

    @Route("")
    public void shopInfo(@Sender CommandSender sender) {
        if (sender instanceof Player) {
            Block target = ((Player) sender).getTargetBlockExact(5);
            if (target != null) {
                Sign sign = null;
                if (chestShopSign.isValid(target)) {
                    sign = (Sign) target.getState();
                } else if (shopBlockService.couldBeShopContainer(target)) {
                    sign = shopBlockService.getConnectedSign(target);
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
