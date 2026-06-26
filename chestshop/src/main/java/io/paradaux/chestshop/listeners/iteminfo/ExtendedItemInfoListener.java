package io.paradaux.chestshop.listeners.iteminfo;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.commands.ItemInfo;
import io.paradaux.chestshop.events.ItemInfoEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.map.MapView;
import org.bukkit.potion.PotionEffect;

import java.util.Map;

import static io.paradaux.chestshop.utils.NumberUtil.toTime;
import static io.paradaux.chestshop.utils.StringUtil.capitalizeFirstLetter;

/**
 * Enriches the {@code /iteminfo} output with item-type-specific details (maps,
 * crossbows, axolotl buckets, bundles, armour trims, potions). These handlers
 * were previously spread across the version-named {@code adapter/} classes
 * (Spigot_1_14/1_17/1_20/1_20_5); they all target the same modern Paper API the
 * plugin compiles against, so they live together here, keyed only by item type.
 *
 * @author Acrobot
 */
public class ExtendedItemInfoListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public static void addMapInfo(ItemInfoEvent event) {
        if (event.getItem().hasItemMeta()) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta instanceof MapMeta) {
                if (((MapMeta) meta).getMapView() != null) {
                    MapView mapView = ((MapMeta) meta).getMapView();
                    event.addMessage("chestshop.iteminfo_map_view",
                            "id", String.valueOf(mapView.getId()),
                            "x", String.valueOf(mapView.getCenterX()),
                            "z", String.valueOf(mapView.getCenterZ()),
                            "world", mapView.getWorld() != null ? mapView.getWorld().getName() : "unknown",
                            "scale", capitalizeFirstLetter(mapView.getScale().name(), '_'),
                            "locked", String.valueOf(mapView.isLocked())
                    );
                }
                if (((MapMeta) meta).hasLocationName()) {
                    event.addMessage("chestshop.iteminfo_map_location", "location", String.valueOf(((MapMeta) meta).getLocationName()));
                }
            }
        }
    }

    @EventHandler
    public void addCrossBowInfo(ItemInfoEvent event) {
        if (event.getItem().hasItemMeta()) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta instanceof CrossbowMeta && ((CrossbowMeta) meta).hasChargedProjectiles()) {
                event.addMessage("chestshop.iteminfo_crossbow_projectiles");
                for (ItemStack chargedProjectile : ((CrossbowMeta) meta).getChargedProjectiles()) {
                    ItemInfo.sendItemName(event.getSender(), chargedProjectile, "chestshop.iteminfo_crossbow_projectile");
                    ItemInfoEvent projectileEvent = ChestShop.callEvent(new ItemInfoEvent(event.getSender(), chargedProjectile));
                    for (Map.Entry<String, Component> entry : projectileEvent.getMessages()) {
                        event.addRawMessage("crossbow_projectile_" + chargedProjectile.hashCode() + "_" + entry.getKey(), entry.getValue());
                    }
                    event.addRawMessage("crossbow_projectile_" + chargedProjectile.hashCode() + "_divider", ChatColor.GRAY + "---");
                }
            }
        }
    }

    @EventHandler
    public void addAxolotlInfo(ItemInfoEvent event) {
        if (event.getItem().hasItemMeta()) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta instanceof AxolotlBucketMeta) {
                event.addMessage("chestshop.iteminfo_axolotl_variant", "variant", capitalizeFirstLetter(((AxolotlBucketMeta) meta).getVariant().name(), '_'));
            }
        }
    }

    @EventHandler
    public void addBundleInfo(ItemInfoEvent event) {
        if (event.getItem().hasItemMeta()) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta instanceof BundleMeta) {
                event.addMessage("chestshop.iteminfo_bundle_items", "itemcount", String.valueOf(((BundleMeta) meta).getItems().size()));
            }
        }
    }

    @EventHandler
    public void addArmorInfo(ItemInfoEvent event) {
        if (event.getItem().hasItemMeta()) {
            ItemMeta meta = event.getItem().getItemMeta();
            if (meta instanceof ArmorMeta && ((ArmorMeta) meta).hasTrim()) {
                event.addMessage("chestshop.iteminfo_armor_trim",
                        "pattern", capitalizeFirstLetter(((ArmorMeta) meta).getTrim().getPattern().getKey().getKey(), '_'),
                        "material", capitalizeFirstLetter(((ArmorMeta) meta).getTrim().getMaterial().getKey().getKey(), '_'));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public static void addPotionInfo(ItemInfoEvent event) {
        ItemStack item = event.getItem();
        if (!item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof PotionMeta)) {
            return;
        }

        PotionMeta potionMeta = (PotionMeta) meta;

        StringBuilder message = new StringBuilder();
        if (potionMeta.getBasePotionType() != null) {

            message.append(ChatColor.GRAY);

            message.append(capitalizeFirstLetter(item.getType().name(), '_')).append(" of ");
            message.append(capitalizeFirstLetter(potionMeta.getBasePotionType().getKey().getKey(), '_')).append(' ');

        }

        for (PotionEffect effect : potionMeta.getCustomEffects()) {
            if (message.length() > 0) {
                message.append('\n');
            }
            message.append(ChatColor.DARK_GRAY + capitalizeFirstLetter(effect.getType().getKey().getKey(), '_')
                    + ' ' + (effect.getAmplifier() + 1) + ' ' + toTime(effect.getDuration() / 20));
        }
        if (message.length() > 0) {
            event.addRawMessage("iteminfo_potion", message.toString());
        }
    }
}
