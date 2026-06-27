package io.paradaux.chestshop.listeners.preshopcreation;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.PreShopCreationEvent;

/**
 * @author Acrobot
 */
public class ErrorMessageSender {

    public static void onPreShopCreation(PreShopCreationEvent event) {
        if (!event.isCancelled()) {
            return;
        }

        String message = null;

        switch (event.getOutcome()) {
            case UNKNOWN_PLAYER:
                message = "chestshop.PLAYER_NOT_FOUND";
                break;
            case INVALID_ITEM:
                message = "chestshop.INCORRECT_ITEM_ID";
                break;
            case INVALID_PRICE:
                message = "chestshop.INVALID_SHOP_PRICE";
                break;
            case INVALID_QUANTITY:
                message = "chestshop.INVALID_SHOP_QUANTITY";
                break;
            case SELL_PRICE_HIGHER_THAN_BUY_PRICE:
                message = "chestshop.SELL_PRICE_HIGHER_THAN_BUY_PRICE";
                break;
            case NO_CHEST:
                message = "chestshop.NO_CHEST_DETECTED";
                break;
            case NO_PERMISSION:
                message = "chestshop.NO_PERMISSION";
                break;
            case NO_PERMISSION_FOR_TERRAIN:
                message = "chestshop.CANNOT_CREATE_SHOP_HERE";
                break;
            case NO_PERMISSION_FOR_CHEST:
                message = "chestshop.CANNOT_ACCESS_THE_CHEST";
                break;
            case NOT_ENOUGH_MONEY:
                message = "chestshop.NOT_ENOUGH_MONEY";
                break;
            case ITEM_AUTOFILL:
                message = "chestshop.CLICK_TO_AUTOFILL_ITEM";
                break;
            default:
                break;
        }

        if (message != null) {
            ChestShop.message().send(event.getPlayer(), message);
        }
    }
}
