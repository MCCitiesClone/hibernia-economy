package io.paradaux.chestshop.listeners.account;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.events.AccountAccessEvent;
import io.paradaux.chestshop.events.AccountQueryEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Thin entrypoint for the two account events — both genuine integration points
 * Treasury hooks into ({@link AccountQueryEvent} to resolve business accounts,
 * {@link AccountAccessEvent} to check firm permissions). Each is handed to
 * {@link io.paradaux.chestshop.services.AccountService} via {@link ChestShop#accounts()}.
 * Replaces the listener half of the former {@code NameManager} God-class.
 */
public class AccountListener implements Listener {

    @EventHandler
    public void onAccountQuery(AccountQueryEvent event) {
        ChestShop.accounts().onAccountQuery(event);
    }

    @EventHandler
    public void onAccountAccessCheck(AccountAccessEvent event) {
        ChestShop.accounts().onAccountAccessCheck(event);
    }
}
