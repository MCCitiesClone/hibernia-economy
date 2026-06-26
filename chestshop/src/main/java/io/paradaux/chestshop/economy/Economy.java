package io.paradaux.chestshop.economy;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.inventory.Inventory;

import java.math.BigDecimal;

/**
 * @author Acrobot
 *         Economy management
 */
public class Economy {
    /**
     * Get the name of the server conomy account
     * @return The username of te server economy account
     * @deprecated Use {@link io.paradaux.chestshop.services.AccountService#getServerEconomyAccount()} or {@link Properties#SERVER_ECONOMY_ACCOUNT}
     */
    @Deprecated
    public static String getServerAccountName() {
        return Properties.SERVER_ECONOMY_ACCOUNT;
    }

    public static boolean isOwnerEconomicallyActive(Inventory inventory) {
        return !ChestShopSign.isAdminShop(inventory) || ChestShop.accounts().getServerEconomyAccount() != null;
    }

    public static String formatBalance(BigDecimal amount) {
        return ChestShop.economy().format(amount);
    }

    /**
     * @deprecated Use {@link #formatBalance(BigDecimal)}
     */
    @Deprecated
    public static String formatBalance(double amount) {
        return formatBalance(BigDecimal.valueOf(amount));
    }
}
