package io.paradaux.chestshop.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.dao.ItemCodeRepository;
import io.paradaux.chestshop.utils.encoding.Base62;
import io.paradaux.chestshop.utils.encoding.Base64;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.YamlConstructor;
import org.bukkit.configuration.file.YamlRepresenter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Owns the item-code <em>logic</em>: serialising an {@link ItemStack} to/from the
 * short Base62 code shops put on signs, the find-or-create that keeps the store
 * deduplicated, and the one-time re-serialisation when the server's item data
 * version changes. All persistence is delegated to {@link ItemCodeRepository}.
 *
 * <p>This is the service half of the {@code ItemDatabase} split: the old class
 * mixed YAML/Base64 encoding, find-or-create business rules, and raw ORMlite/SQLite
 * access in one place; that DB access now lives behind the repository, leaving this
 * a pure, testable service.
 */
@Singleton
public class ItemCodeService {

    private final ItemCodeRepository repository;
    private final Yaml yaml = new Yaml(new YamlBukkitConstructor(), new YamlRepresenter(), new DumperOptions());

    @Inject
    public ItemCodeService(ItemCodeRepository repository) {
        this.repository = repository;
    }

    /** Runs the one-time metadata re-serialisation if the server's data version advanced. */
    public void migrateIfNeeded() {
        File versionFile = ChestShop.loadFile("version");
        YamlConfiguration versionConfig = YamlConfiguration.loadConfiguration(versionFile);

        int previousVersion = versionConfig.getInt("metadata-version", -1);
        int newVersion = currentMetadataVersion();
        if (previousVersion >= newVersion) {
            return;
        }
        if (reserialiseAll(previousVersion, newVersion)) {
            versionConfig.set("metadata-version", newVersion);
            try {
                versionConfig.save(versionFile);
            } catch (IOException e) {
                ChestShop.getBukkitLogger().log(Level.SEVERE,
                        "Error while updating metadata-version from " + previousVersion + " to " + newVersion, e);
            }
        } else {
            ChestShop.getBukkitLogger().log(Level.WARNING,
                    "Error while updating Item Metadata database! While the plugin will still run it will work less efficiently.");
        }
    }

    /** The short Base62 code for an item, creating a store row for it if new. */
    public String getItemCode(ItemStack item) {
        try {
            ItemStack clone = new ItemStack(item);
            clone.setAmount(1);

            // Preserved verbatim from the old ItemDatabase: this resets damage on a
            // throwaway meta copy and never re-applies it, so it is effectively a
            // no-op. Kept as-is so item codes are byte-identical to existing shops —
            // actually applying it would change the generated code for damaged items.
            ItemMeta meta = clone.getItemMeta();
            if (meta instanceof Damageable damageable && damageable.hasDamage()) {
                damageable.setDamage(0);
            }

            String dumped = yaml.dump(clone);
            ItemStack loadedItem = yaml.loadAs(dumped, ItemStack.class);
            if (!loadedItem.isSimilar(item)) {
                dumped = yaml.dump(loadedItem);
            }
            String blob = Base64.encodeObject(dumped);

            int id = repository.findIdByBlob(blob).orElseGet(() -> repository.insert(blob));
            return Base62.encode(id);
        } catch (IOException e) {
            ChestShop.getBukkitLogger().log(Level.SEVERE, "Unable to get code of item " + item, e);
            return null;
        }
    }

    /** The {@link ItemStack} a code refers to, or {@code null} if unknown/corrupt. */
    public ItemStack getFromCode(String code) {
        int id = Base62.decode(code);
        Optional<String> blob = repository.findBlobById(id);
        if (blob.isEmpty()) {
            return null;
        }
        try {
            return yaml.loadAs((String) Base64.decodeToObject(blob.get()), ItemStack.class);
        } catch (YAMLException e) {
            ChestShop.getBukkitLogger().log(Level.SEVERE,
                    "YAML of the item with ID " + code + " (" + id + ") is corrupted: \n" + blob.get());
        } catch (IOException | ClassNotFoundException e) {
            ChestShop.getBukkitLogger().log(Level.SEVERE, "Unable to load item with ID " + code + " (" + id + ")", e);
        } catch (StackOverflowError e) {
            ChestShop.getBukkitLogger().log(Level.SEVERE, "Item with ID " + code + " (" + id + ") is corrupted. Sorry :(");
        }
        return null;
    }

    private int currentMetadataVersion() {
        ItemStack item = new ItemStack(Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("GetCurrentMetadataVersion");
            item.setItemMeta(meta);
        }
        Map<String, Object> serialized = item.serialize();
        return (int) serialized.getOrDefault("v", -1);
    }

    private boolean reserialiseAll(int previousVersion, int newVersion) {
        if (previousVersion > -1) {
            ChestShop.getBukkitLogger().info("Data version change detected! Previous version was " + previousVersion);
        }
        ChestShop.getBukkitLogger().info("Updating Item Metadata database to data version " + newVersion + "...");

        AtomicInteger seen = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        long start = System.currentTimeMillis();
        try {
            repository.forEach(stored -> {
                seen.incrementAndGet();
                try {
                    String serialized = (String) Base64.decodeToObject(stored.blob());
                    // Cheap version sniff: re-serialise only items not already at newVersion.
                    if (previousVersion < 0 || !serialized.contains("\nv: " + newVersion + "\n")) {
                        try {
                            ItemStack itemStack = yaml.loadAs(serialized, ItemStack.class);
                            repository.updateBlob(stored.id(), Base64.encodeObject(yaml.dump(itemStack)));
                            updated.incrementAndGet();
                        } catch (RuntimeException e) {
                            ChestShop.getBukkitLogger().log(Level.SEVERE, "YAML of the item with ID "
                                    + Base62.encode(stored.id()) + " (" + stored.id() + ") is corrupted: \n"
                                    + serialized + "\n" + e.getMessage());
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    ChestShop.getBukkitLogger().log(Level.SEVERE, "Unable to convert item with ID "
                            + Base62.encode(stored.id()) + " (" + stored.id() + ")", e);
                } catch (StackOverflowError e) {
                    ChestShop.getBukkitLogger().log(Level.SEVERE, "Item with ID "
                            + Base62.encode(stored.id()) + " (" + stored.id() + ") is corrupted. Sorry :(");
                }
                if (seen.get() % 1000 == 0) {
                    ChestShop.getBukkitLogger().info("Checked " + seen + " items. Updated " + updated + "...");
                }
            });
        } catch (RuntimeException e) {
            ChestShop.getBukkitLogger().log(Level.SEVERE,
                    "Unable to update metadata version of all items from " + previousVersion + " to " + newVersion, e);
            return false;
        }

        ChestShop.getBukkitLogger().info("Finished updating database in " + (System.currentTimeMillis() - start) / 1000.0
                + "s. " + updated + " items out of " + seen + " were updated!");
        return true;
    }

    private static final class YamlBukkitConstructor extends YamlConstructor {
        YamlBukkitConstructor() {
            this.yamlConstructors.put(new Tag(Tag.PREFIX + "org.bukkit.inventory.ItemStack"), yamlConstructors.get(Tag.MAP));
        }
    }
}
