package io.paradaux.chestshop.events;

import io.paradaux.chestshop.ChestShop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an {@code /iteminfo} call. Handlers contribute info lines, which are
 * accumulated as pre-rendered (prefix-less) {@link Component}s keyed by a stable
 * string so duplicates collapse and order is preserved; the caller sends them in
 * order. Lines come either from a {@code messages.properties} key (rendered through
 * the framework {@link io.paradaux.hibernia.framework.i18n.Message}) or as a raw
 * component / legacy-section string.
 *
 * @author Acrobot
 */
public class ItemInfoEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final CommandSender sender;
    private final ItemStack item;

    private final Map<String, Component> messages = new LinkedHashMap<>();

    public ItemInfoEvent(CommandSender sender, ItemStack item) {
        this.sender = sender;
        this.item = item;
    }

    /**
     * @return CommandSender who initiated the call
     */
    public CommandSender getSender() {
        return sender;
    }

    /**
     * @return Item recognised by /iteminfo
     */
    public ItemStack getItem() {
        return item;
    }

    /**
     * Add a line rendered from a {@code messages.properties} key (prefix blanked),
     * stored under that key.
     * @param key  the message key (e.g. {@code chestshop.iteminfo_repaircost})
     * @param args alternating placeholder name/value pairs
     */
    public void addMessage(String key, String... args) {
        messages.put(key, render(key, args));
    }

    public void addRawMessage(String key, Component message) {
        messages.put(key, message);
    }

    public void addRawMessage(String key, String message) {
        // Raw item-info lines (lore/enchantments/potions) carry §-section legacy
        // colour codes (ChatColor.*), so deserialize them natively.
        messages.put(key, LegacyComponentSerializer.legacySection().deserialize(message));
    }

    /**
     * @return the accumulated lines, each as a key → pre-rendered {@link Component} entry, in order
     */
    public Collection<Map.Entry<String, Component>> getMessages() {
        return messages.entrySet();
    }

    private static Component render(String key, String... args) {
        return ChestShop.message().component(key, ChestShop.values(false, null, args));
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
