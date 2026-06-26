package io.paradaux.chestshop.listeners.item;

import io.paradaux.chestshop.utils.MaterialUtil;
import io.paradaux.chestshop.events.ItemStringQueryEvent;

/**
 * The vanilla fallback that names an item from its material. Invoked directly by
 * {@link io.paradaux.chestshop.services.ItemService#queryString} (was the @NORMAL
 * ItemStringQueryEvent listener).
 */
public class ItemStringListener {

    public static void calculateItemString(ItemStringQueryEvent event) {
        if (event.getItemString() == null) {
            event.setItemString(MaterialUtil.getName(event.getItem(), event.getMaxWidth()));
        }
    }

}
