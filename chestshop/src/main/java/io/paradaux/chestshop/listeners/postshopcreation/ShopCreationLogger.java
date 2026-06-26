package io.paradaux.chestshop.listeners.postshopcreation;

import io.paradaux.chestshop.utils.LocationUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.ShopCreatedEvent;
import io.paradaux.chestshop.signs.ChestShopSign;

/**
 * Writes a shop-creation line to the shop log. Invoked directly by
 * {@link io.paradaux.chestshop.services.ShopService#onCreated} (was a @MONITOR
 * ShopCreatedEvent listener).
 *
 * @author Acrobot
 */
public class ShopCreationLogger {
    private static final String CREATION_MESSAGE = "%1$s created %2$s - %3$s - %4$s - at %5$s";

    public static void onShopCreation(final ShopCreatedEvent event) {
        ChestShop.runInAsyncThread(() -> {
            String creator = event.getPlayer().getName();
            String shopOwner = ChestShopSign.getOwner(event.getSignLines());
            String typeOfShop = ChestShopSign.isAdminShop(shopOwner) ? "an Admin Shop" : "a shop" + (event.createdByOwner() ? "" : " for " + event.getOwnerAccount().getName());

            String item = ChestShopSign.getQuantity(event.getSignLines()) + ' ' + ChestShopSign.getItem(event.getSignLines());
            String prices = ChestShopSign.getPrice(event.getSignLines());
            String location = LocationUtil.locationToString(event.getSign().getLocation());

            String message = String.format(CREATION_MESSAGE,
                    creator,
                    typeOfShop,
                    item,
                    prices,
                    location);

            ChestShop.getShopLogger().info(message);
        });
    }
}
