package io.paradaux.chestshop.players;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.events.AccountAccessEvent;
import io.paradaux.chestshop.events.AccountQueryEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * The account entrypoint and a legacy static facade. The persistence, caching, and
 * account business logic moved to {@link io.paradaux.chestshop.services.AccountService}
 * (reached via {@link ChestShop#accounts()}); this class now only:
 *
 * <ul>
 *   <li>registers the two account {@link Listener} handlers ({@link AccountQueryEvent},
 *       {@link AccountAccessEvent} — both genuine integration points Treasury hooks into),
 *       delegating each to the service; and</li>
 *   <li>keeps the original {@code static} methods as thin delegators so existing callers
 *       compile unchanged while they're migrated to the service over later phases.</li>
 * </ul>
 *
 * @author Andrzej Pomirski (Acrobot)
 */
public class NameManager implements Listener {

    public static int getAccountCount() {
        return ChestShop.accounts().getAccountCount();
    }

    public static Account getOrCreateAccount(OfflinePlayer player) {
        return ChestShop.accounts().getOrCreateAccount(player);
    }

    public static Account getOrCreateAccount(UUID id, String name) {
        return ChestShop.accounts().getOrCreateAccount(id, name);
    }

    public static Account getAccount(UUID uuid) {
        return ChestShop.accounts().getAccount(uuid);
    }

    public static Account getAccount(String fullName) {
        return ChestShop.accounts().getAccount(fullName);
    }

    /**
     * @deprecated Use the {@link AccountQueryEvent} instead!
     */
    @Deprecated
    public static Account getAccountFromShortName(String shortName) {
        return ChestShop.accounts().getAccountFromShortName(shortName);
    }

    @EventHandler
    public static void onAccountQuery(AccountQueryEvent event) {
        ChestShop.accounts().onAccountQuery(event);
    }

    public static Account storeUsername(PlayerDTO player) {
        return ChestShop.accounts().storeUsername(player);
    }

    public static void storeAccount(Account account) {
        ChestShop.accounts().storeAccount(account);
    }

    public static boolean canUseName(Player player, Permission base, String name) {
        return ChestShop.accounts().canUseName(player, base, name);
    }

    @EventHandler
    public static void onAccountAccessCheck(AccountAccessEvent event) {
        ChestShop.accounts().onAccountAccessCheck(event);
    }

    public static boolean isAdminShop(UUID uuid) {
        return ChestShop.accounts().isAdminShop(uuid);
    }

    public static boolean isServerEconomyAccount(UUID uuid) {
        return ChestShop.accounts().isServerEconomyAccount(uuid);
    }

    public static void load() {
        ChestShop.accounts().load();
    }

    public static Account getServerEconomyAccount() {
        return ChestShop.accounts().getServerEconomyAccount();
    }

    public static void setUuidVersion(int uuidVersion) {
        ChestShop.accounts().setUuidVersion(uuidVersion);
    }

    public static int getUuidVersion() {
        return ChestShop.accounts().getUuidVersion();
    }
}
