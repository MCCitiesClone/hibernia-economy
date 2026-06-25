package io.paradaux.chestshop.adapter;

import io.paradaux.chestshop.breeze.utils.BlockUtil;
import io.paradaux.chestshop.breeze.utils.ImplementationAdapter;
import io.paradaux.chestshop.configuration.Messages;
import io.paradaux.chestshop.events.ItemInfoEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;

import static io.paradaux.chestshop.breeze.utils.StringUtil.capitalizeFirstLetter;
import static io.paradaux.chestshop.configuration.Messages.iteminfo_armor_trim;

public class Spigot_1_20 implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public static void onSignChange(SignChangeEvent event) {
        Block signBlock = event.getBlock();

        if (!BlockUtil.isSign(signBlock)) {
            return;
        }

        if (event.getSide() != Side.FRONT) {
            Sign sign = (Sign) ImplementationAdapter.getState(signBlock, false);
            if (ChestShopSign.isValid(sign)) {
                event.setCancelled(true);
                Messages.CANNOT_CHANGE_SIGN_BACKSIDE.sendWithPrefix(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void addArmorInfo(ItemInfoEvent event) {
        if (event.getItem().hasItemMeta()) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta instanceof ArmorMeta && ((ArmorMeta) meta).hasTrim()) {
                event.addMessage(iteminfo_armor_trim,
                        "pattern", capitalizeFirstLetter(((ArmorMeta) meta).getTrim().getPattern().getKey().getKey(), '_'),
                        "material", capitalizeFirstLetter(((ArmorMeta) meta).getTrim().getMaterial().getKey().getKey(), '_'));
            }
        }
    }
}
