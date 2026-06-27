package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.PreShopCreationEvent;

/**
 * Thin entrypoint: charges the shop-creation fee through
 * {@link io.paradaux.chestshop.services.ShopService}, failing the creation with
 * {@code NOT_ENOUGH_MONEY} when the player can't afford it.
 *
 * @author Acrobot
 */
public class CreationFeeGetter {

    public static void onShopCreation(PreShopCreationEvent event) {
        if (!ChestShop.shops().chargeCreationFee(event.getPlayer(), event.getSignLines())) {
            event.setOutcome(PreShopCreationEvent.CreationOutcome.NOT_ENOUGH_MONEY);
            event.setSignLines(new String[4]);
        }
    }
}
