package io.paradaux.chestshop.events;

import org.bukkit.inventory.ItemStack;

/**
 * Mutable carrier for parsing a sign item string into an {@link ItemStack}, threaded
 * through {@link io.paradaux.chestshop.services.ItemService#parse}'s ordered resolvers
 * (ItemBridge → alias → vanilla). Formerly a Bukkit event.
 */
public class ItemParseEvent {

    private final String itemString;
    private ItemStack item = null;

    public ItemParseEvent(String itemString) {
        this.itemString = itemString;
    }

    /**
     * Get the item string that should be parsed
     * @return The item string to parse
     */
    public String getItemString() {
        return itemString;
    }

    /**
     * Set the item that the string represents
     * @param item The item for the string
     */
    public void setItem(ItemStack item) {
        this.item = item;
    }

    /**
     * The item that was parsed
     * @return The parsed item or null if none was found
     */
    public ItemStack getItem() {
        return item;
    }

    /**
     * Whether or not the item string of this event has a parsed item
     * @return True if an item was successfully parsed; false if not
     */
    public boolean hasItem() {
        return item != null;
    }
}
