package io.paradaux.chestshop.adapter;

import io.paradaux.chestshop.events.ItemInfoEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import static io.paradaux.chestshop.breeze.utils.StringUtil.capitalizeFirstLetter;
import static io.paradaux.chestshop.configuration.Messages.iteminfo_axolotl_variant;
import static io.paradaux.chestshop.configuration.Messages.iteminfo_bundle_items;

public class Spigot_1_17 implements Listener {

    @EventHandler
    public void addAxolotlInfo(ItemInfoEvent event) {
        if (event.getItem().hasItemMeta()) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta instanceof AxolotlBucketMeta) {
                event.addMessage(iteminfo_axolotl_variant, "variant", capitalizeFirstLetter(((AxolotlBucketMeta) meta).getVariant().name(), '_'));
            }
        }
    }

    @EventHandler
    public void addBundleInfo(ItemInfoEvent event) {
        if (event.getItem().hasItemMeta()) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta instanceof BundleMeta) {
                event.addMessage(iteminfo_bundle_items, "itemcount", String.valueOf(((BundleMeta) meta).getItems().size()));
            }
        }
    }
}
