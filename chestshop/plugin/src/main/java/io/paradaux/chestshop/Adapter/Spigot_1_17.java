package io.paradaux.chestshop.Adapter;

import io.paradaux.chestshop.Events.ItemInfoEvent;
import io.paradaux.chestshop.Utils.VersionAdapter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import static io.paradaux.chestshop.breeze.Utils.StringUtil.capitalizeFirstLetter;
import static io.paradaux.chestshop.Configuration.Messages.iteminfo_axolotl_variant;
import static io.paradaux.chestshop.Configuration.Messages.iteminfo_bundle_items;

public class Spigot_1_17 implements Listener, VersionAdapter {

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

    @Override
    public boolean isSupported() {
        try {
            Class.forName("org.bukkit.inventory.meta.AxolotlBucketMeta");
            Class.forName("org.bukkit.inventory.meta.BundleMeta");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
