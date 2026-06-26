package io.paradaux.chestshop.listeners.economy;

import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.events.economy.*;
import io.paradaux.chestshop.ChestShop;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * @author Acrobot
 */
public class ServerAccountCorrector implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public static void onCurrencyTransfer(CurrencyTransferEvent event) {
        UUID partner = event.getPartner();

        if (!ChestShop.accounts().isAdminShop(partner) || ChestShop.accounts().isServerEconomyAccount(partner)) {
            return;
        }

        Account account = ChestShop.accounts().getServerEconomyAccount();
        partner = account != null ? account.getUuid() : null;

        if (partner == null) {
            return;
        }

        event.setPartner(partner);
    }
}
