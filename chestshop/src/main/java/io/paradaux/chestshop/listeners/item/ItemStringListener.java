package io.paradaux.chestshop.listeners.item;

import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.events.ItemStringQueryEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ItemStringListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public static void calculateItemString(ItemStringQueryEvent event) {
        if (event.getItemString() == null) {
            event.setItemString(MaterialUtil.getName(event.getItem(), event.getMaxWidth()));
        }
    }

}
