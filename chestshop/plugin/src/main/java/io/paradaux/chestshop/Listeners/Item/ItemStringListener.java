package io.paradaux.chestshop.Listeners.Item;

import io.paradaux.chestshop.breeze.Utils.MaterialUtil;
import io.paradaux.chestshop.Events.ItemStringQueryEvent;
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
