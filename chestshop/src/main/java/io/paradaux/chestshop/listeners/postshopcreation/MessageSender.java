package io.paradaux.chestshop.listeners.postshopcreation;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.ShopCreatedEvent;

/**
 * Sends the "shop created" confirmation to the creator. Invoked directly by
 * {@link io.paradaux.chestshop.services.ShopService#onCreated} (was a @MONITOR
 * ShopCreatedEvent listener).
 *
 * @author Acrobot
 */
public class MessageSender {

    public static void onShopCreation(ShopCreatedEvent event) {
        ChestShop.message().send(event.getPlayer(), "chestshop.SHOP_CREATED");
    }
}
