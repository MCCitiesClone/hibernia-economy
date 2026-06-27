package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.utils.StringUtil;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.ItemUtil;
import io.paradaux.chestshop.utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Matcher;

import static io.paradaux.chestshop.utils.MaterialUtil.*;
import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.INVALID_ITEM;
import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.ITEM_AUTOFILL;
import static io.paradaux.chestshop.signs.ChestShopSign.ITEM_LINE;
import static io.paradaux.chestshop.signs.ChestShopSign.AUTOFILL_CODE;

/**
 * @author Acrobot
 */
public class ItemChecker {

    public static void onPreShopCreation(PreShopCreationEvent event) {
        String itemCode = ChestShopSign.getItem(event.getSignLines());

        ItemStack item = ChestShop.items().parse(itemCode);

        if (item == null) {
            if (Properties.ALLOW_AUTO_ITEM_FILL && itemCode.equals(AUTOFILL_CODE)) {
                Container container = uBlock.findConnectedContainer(event.getSign());
                if (container != null) {
                    for (ItemStack stack : container.getInventory().getContents()) {
                        if (!MaterialUtil.isEmpty(stack)) {
                            item = stack;
                            break;
                        }
                    }
                }

                if (item == null) {
                    event.setSignLine(ITEM_LINE, ChatColor.BOLD + ChestShopSign.AUTOFILL_CODE);
                    event.setOutcome(ITEM_AUTOFILL);
                    return;
                }
            } else {
                event.setOutcome(INVALID_ITEM);
                return;
            }
        }

        itemCode = ItemUtil.getSignName(item);

        if (StringUtil.getMinecraftStringWidth(itemCode) > MAXIMUM_SIGN_WIDTH) {
            event.setOutcome(INVALID_ITEM);
            return;
        }

        event.setSignLine(ITEM_LINE, itemCode);
    }

    private static boolean isSameItem(String newCode, ItemStack item) {
        ItemStack newItem = ChestShop.items().parse(newCode);

        return newItem != null && MaterialUtil.equals(newItem, item);
    }

    private static String getMetadata(String itemCode) {
        Matcher m = METADATA.matcher(itemCode);

        if (!m.find()) {
            return "";
        }

        return m.group();
    }
}
