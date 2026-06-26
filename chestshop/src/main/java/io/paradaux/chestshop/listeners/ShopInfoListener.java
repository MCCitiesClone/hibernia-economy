package io.paradaux.chestshop.listeners;

import io.paradaux.chestshop.utils.InventoryUtil;
import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.utils.PriceUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.AccountQueryEvent;
import io.paradaux.chestshop.events.economy.CurrencyFormatEvent;
import io.paradaux.chestshop.events.ItemInfoEvent;
import io.paradaux.chestshop.events.ItemParseEvent;
import io.paradaux.chestshop.events.ShopInfoEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.ItemUtil;
import io.paradaux.chestshop.utils.uBlock;
import com.google.common.collect.ImmutableMap;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @author Acrobot
 */
public class ShopInfoListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public static void showShopInfo(ShopInfoEvent event) {
        if (ChestShopSign.isValid(event.getSign())) {
            String nameLine = ChestShopSign.getOwner(event.getSign());
            int amount;
            try {
                amount = ChestShopSign.getQuantity(event.getSign());
            } catch (NumberFormatException notANumber) {
                ChestShop.message().send(event.getSender(), "chestshop.INVALID_SHOP_DETECTED");
                return;
            }
            String pricesLine = ChestShopSign.getPrice(event.getSign());

            AccountQueryEvent queryEvent = new AccountQueryEvent(nameLine);
            ChestShop.callEvent(queryEvent);
            if (queryEvent.getAccount() == null) {
                ChestShop.message().send(event.getSender(), "chestshop.INVALID_SHOP_DETECTED");
                return;
            }

            String ownerName = queryEvent.getAccount().getName();
            ownerName = ownerName != null ? ownerName : nameLine;

            ItemParseEvent parseEvent = new ItemParseEvent(ChestShopSign.getItem(event.getSign()));
            ItemStack item = ChestShop.callEvent(parseEvent).getItem();
            if (item == null || amount < 1) {
                ChestShop.message().send(event.getSender(), "chestshop.INVALID_SHOP_DETECTED");
                return;
            }

            Container shopBlock = uBlock.findConnectedContainer(event.getSign());
            String stock;
            if (shopBlock != null) {
                stock = String.valueOf(InventoryUtil.getAmount(item, shopBlock.getInventory()));
            } else {
                stock = "\u221e"; // Infinity symbol
            }

            Map<String, String> replacementMap = ImmutableMap.of(
                    "item", ItemUtil.getName(item),
                    "stock", stock,
                    "owner", ownerName,
                    "prices", pricesLine,
                    "quantity", String.valueOf(amount)
            );
            if (!Properties.SHOWITEM_MESSAGE
                    || !MaterialUtil.Show.sendMessage(event.getSender(), event.getSender().getName(), "chestshop.shopinfo", false, new ItemStack[]{item}, replacementMap)) {
                event.getSender().sendMessage(ChestShop.message().component("chestshop.shopinfo", ChestShop.values(false, replacementMap)));
            }


            BigDecimal buyPrice = PriceUtil.getExactBuyPrice(pricesLine);
            BigDecimal sellPrice = PriceUtil.getExactSellPrice(pricesLine);

            ItemInfoEvent itemInfoEvent = ChestShop.callEvent(new ItemInfoEvent(event.getSender(), item));

            for (Map.Entry<String, Component> entry : itemInfoEvent.getMessages()) {
                event.getSender().sendMessage(entry.getValue());
            }

            if (!buyPrice.equals(PriceUtil.NO_PRICE)) {
                CurrencyFormatEvent cfe = ChestShop.callEvent(new CurrencyFormatEvent(buyPrice));
                ChestShop.message().send(event.getSender(), "chestshop.shopinfo_buy", "prefix", "",
                        "amount", String.valueOf(amount),
                        "price", cfe.getFormattedAmount()
                );
            }
            if (!sellPrice.equals(PriceUtil.NO_PRICE)) {
                CurrencyFormatEvent cfe = ChestShop.callEvent(new CurrencyFormatEvent(sellPrice));
                ChestShop.message().send(event.getSender(), "chestshop.shopinfo_sell", "prefix", "",
                        "amount", String.valueOf(amount),
                        "price", cfe.getFormattedAmount()
                );
            }
        } else {
            ChestShop.message().send(event.getSender(), "chestshop.INVALID_SHOP_DETECTED");
        }
    }
}
