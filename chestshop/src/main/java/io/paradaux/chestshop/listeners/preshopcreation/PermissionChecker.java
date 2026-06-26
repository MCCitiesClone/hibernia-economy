package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.utils.PriceUtil;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.ChestShop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.NO_PERMISSION;
import static io.paradaux.chestshop.Permission.*;
import static io.paradaux.chestshop.signs.ChestShopSign.NAME_LINE;
import static org.bukkit.event.EventPriority.HIGH;

/**
 * @author Acrobot
 */
public class PermissionChecker implements Listener {

    @EventHandler(priority = HIGH)
    public static void onPreShopCreation(PreShopCreationEvent event) {
        Player player = event.getPlayer();

        if (event.getOwnerAccount() != null
                && !ChestShop.accounts().canUseName(player, OTHER_NAME_CREATE, event.getOwnerAccount().getShortName())) {
            event.setSignLine(NAME_LINE, "");
            event.setOutcome(NO_PERMISSION);
            return;
        }

        String priceLine = ChestShopSign.getPrice(event.getSignLines());
        String itemLine = ChestShopSign.getItem(event.getSignLines());

        ItemStack item = ChestShop.items().parse(itemLine);

        if (item == null) {
            if ((PriceUtil.hasBuyPrice(priceLine) && !Permission.has(player, SHOP_CREATION_BUY))
                    || (PriceUtil.hasSellPrice(priceLine) && !Permission.has(player, SHOP_CREATION_SELL))) {
                event.setOutcome(NO_PERMISSION);
            }
            return;
        }

        String matID = item.getType().toString().toLowerCase(Locale.ROOT);

        String[] parts = itemLine.split("#", 2);
        if (parts.length == 2 && Permission.hasPermissionSetFalse(player, SHOP_CREATION_ID + matID + "#" + parts[1])) {
            event.setOutcome(NO_PERMISSION);
            return;
        }

        if (PriceUtil.hasBuyPrice(priceLine)) {
            if (Permission.has(player, SHOP_CREATION_BUY_ID + matID)) {
                return;
            }
            if (Permission.has(player, SHOP_CREATION) || (Permission.has(player, SHOP_CREATION_ID + matID) && Permission.has(player, SHOP_CREATION_BUY))) {
                return;
            }
            event.setOutcome(NO_PERMISSION);
            return;
        }

        if (PriceUtil.hasSellPrice(priceLine)) {
            if (Permission.has(player, SHOP_CREATION_SELL_ID + matID)) {
                return;
            }
            if (Permission.has(player, SHOP_CREATION) || (Permission.has(player, SHOP_CREATION_ID + matID) && Permission.has(player, SHOP_CREATION_SELL))) {
                return;
            }
            event.setOutcome(NO_PERMISSION);
            return;
        }
    }
}
