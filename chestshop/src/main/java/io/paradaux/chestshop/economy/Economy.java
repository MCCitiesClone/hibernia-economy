package io.paradaux.chestshop.economy;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.events.economy.CurrencyCheckEvent;
import io.paradaux.chestshop.signs.ChestShopSign;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;

import java.math.BigDecimal;
import java.util.UUID;

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

    /**
     * @deprecated Directly call the {@link CurrencyCheckEvent}
     */
    @Deprecated
    public static boolean hasEnough(UUID name, World world, double amount) {
        CurrencyCheckEvent event = new CurrencyCheckEvent(BigDecimal.valueOf(amount), name, world);
        ChestShop.callEvent(event);

        return event.hasEnough();
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
