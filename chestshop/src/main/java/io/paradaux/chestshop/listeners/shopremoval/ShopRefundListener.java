package io.paradaux.chestshop.listeners.shopremoval;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.ShopDestroyedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Thin entrypoint: issues the shop-removal refund through
 * {@link io.paradaux.chestshop.services.ShopService}.
 *
 * @author Acrobot
 */
public class ShopRefundListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public static void onShopDestroy(ShopDestroyedEvent event) {
        ChestShop.shops().refundOnRemoval(event.getDestroyer(), event.getSign());
    }
}
