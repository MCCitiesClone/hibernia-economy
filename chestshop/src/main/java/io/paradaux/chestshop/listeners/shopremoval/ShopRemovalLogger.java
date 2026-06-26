package io.paradaux.chestshop.listeners.shopremoval;

import io.paradaux.chestshop.utils.LocationUtil;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.ShopDestroyedEvent;
import io.paradaux.chestshop.signs.ChestShopSign;

/**
 * Writes a shop-removal line to the shop log. Invoked directly by
 * {@link io.paradaux.chestshop.services.ShopService#onDestroyed} (was a @MONITOR
 * ShopDestroyedEvent listener).
 *
 * @author Acrobot
 */
public class ShopRemovalLogger {
    private static final String REMOVAL_MESSAGE = "%1$s was removed by %2$s - %3$s - %4$s - at %5$s";

    public static void onShopRemoval(final ShopDestroyedEvent event) {
        if (!Properties.LOG_ALL_SHOP_REMOVALS && event.getDestroyer() != null) {
            return;
        }

        ChestShop.runInAsyncThread(() -> {
            String shopOwner = ChestShopSign.getOwner(event.getSign());
            String typeOfShop = ChestShopSign.isAdminShop(shopOwner) ? "An Admin Shop" : "A shop belonging to " + shopOwner;

            String item = ChestShopSign.getQuantity(event.getSign()) + ' ' + ChestShopSign.getItem(event.getSign());
            String prices = ChestShopSign.getPrice(event.getSign());
            String location = LocationUtil.locationToString(event.getSign().getLocation());

            String message = String.format(REMOVAL_MESSAGE,
                    typeOfShop,
                    event.getDestroyer() != null ? event.getDestroyer().getName() : "???",
                    item,
                    prices,
                    location);

            ChestShop.getShopLogger().info(message);
        });
    }
}
