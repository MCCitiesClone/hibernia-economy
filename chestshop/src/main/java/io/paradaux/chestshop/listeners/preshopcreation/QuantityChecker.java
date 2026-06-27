package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.PreShopCreationEvent;
import io.paradaux.chestshop.signs.ChestShopSign;

import static io.paradaux.chestshop.events.PreShopCreationEvent.CreationOutcome.INVALID_QUANTITY;

/**
 * @author Acrobot
 */
public class QuantityChecker {

    public static void onPreShopCreation(PreShopCreationEvent event) {
        int amount = -1;
        try {
            amount = ChestShopSign.getQuantity(event.getSignLines());
        } catch (NumberFormatException ignored) {} // not a quantity on the line

        if (amount < 1 || amount > Properties.MAX_SHOP_AMOUNT) {
            event.setOutcome(INVALID_QUANTITY);
        }
    }
}
