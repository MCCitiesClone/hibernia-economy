package io.paradaux.chestshop.listeners.postshopcreation;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.ShopCreatedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * @author Acrobot
 */
public class MessageSender implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public static void onShopCreation(ShopCreatedEvent event) {
        ChestShop.message().send(event.getPlayer(), "chestshop.SHOP_CREATED");
    }
}
