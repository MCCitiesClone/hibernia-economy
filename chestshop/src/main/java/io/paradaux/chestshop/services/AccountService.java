package io.paradaux.chestshop.services;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.Permission;
import io.paradaux.chestshop.configuration.Properties;
import io.paradaux.chestshop.dao.AccountRepository;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.events.AccountAccessEvent;
import io.paradaux.chestshop.events.AccountQueryEvent;
import io.paradaux.chestshop.players.PlayerDTO;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.utils.NameUtil;
import io.paradaux.chestshop.utils.NumberUtil;
import io.paradaux.chestshop.utils.SimpleCache;
import io.paradaux.chestshop.utils.encoding.Base62;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

/**
 * Owns the account <em>logic</em>: the username/UUID/short-name caches, get-or-create
 * resolution, shortened-name allocation, the admin and server-economy accounts, and
 * the name-access rules. All persistence is delegated to {@link AccountRepository}.
 *
 * <p>This is the service half of the {@code NameManager} split: the old static
 * God-class mixed raw ORMlite/SQLite access, caching, and these business rules in one
 * place. The DB access now lives behind the repository, leaving this a service that
 * {@link io.paradaux.chestshop.players.NameManager} (a thin listener + legacy static
 * facade) and the rest of the plugin reach through {@link ChestShop#accounts()}.
 */
@Singleton
public class AccountService {

    private final AccountRepository repository;

    private final Object accountsLock = new Object();

    private final SimpleCache<String, Account> usernameToAccount = new SimpleCache<>(Properties.CACHE_SIZE);
    private final SimpleCache<UUID, Account> uuidToAccount = new SimpleCache<>(Properties.CACHE_SIZE);
    private final SimpleCache<String, Account> shortToAccount = new SimpleCache<>(Properties.CACHE_SIZE);
    private final SimpleCache<String, Boolean> invalidPlayers = new SimpleCache<>(Properties.CACHE_SIZE);

    private Account adminAccount;
    private Account serverEconomyAccount;
    private int uuidVersion = -1;

    @Inject
    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public int getAccountCount() {
        try {
            return NumberUtil.toInt(repository.count() - 1);
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    /**
     * Get or create an account for a player (only if the player has both a UUID and a name).
     * @throws IllegalArgumentException when an invalid player object was passed
     */
    public Account getOrCreateAccount(OfflinePlayer player) {
        Preconditions.checkNotNull(player.getName(), "Name of player " + player.getUniqueId() + " is null?");
        Preconditions.checkArgument(player instanceof Player || !Properties.ENSURE_CORRECT_PLAYERID || uuidVersion < 0 || player.getUniqueId().version() == uuidVersion,
                "Invalid OfflinePlayer! " + player.getUniqueId() + " has version " + player.getUniqueId().version() + " and not server version " + uuidVersion + ". " +
                        "If you believe that is an error and your setup allows such UUIDs then set the ENSURE_CORRECT_PLAYERID config option to false.");
        return getOrCreateAccount(player.getUniqueId(), player.getName());
    }

    /**
     * Get or create an account for a player.
     * @throws IllegalArgumentException when id or name are null
     */
    public Account getOrCreateAccount(UUID id, String name) {
        Preconditions.checkNotNull(id, "UUID of player is null?");
        Preconditions.checkNotNull(name, "Name of player " + id + " is null?");

        Account account = getAccount(id);
        if (account == null) {
            account = storeUsername(new PlayerDTO(id, name));
        }
        return account;
    }

    /** Account info from a UUID, or {@code null} if none was found. */
    public Account getAccount(UUID uuid) {
        try {
            synchronized (accountsLock) {
                return uuidToAccount.get(uuid, () -> {
                    try {
                        Account account = repository.findLatestByUuid(uuid).orElse(null);
                        if (account != null) {
                            account.setUuid(uuid);
                            shortToAccount.put(account.getShortName(), account);
                            usernameToAccount.put(account.getName(), account);
                            return account;
                        }
                    } catch (IllegalStateException e) {
                        ChestShop.getBukkitLogger().log(Level.WARNING, "Error while getting account for " + uuid + ":", e);
                    }
                    throw new Exception("Could not find account for " + uuid);
                });
            }
        } catch (ExecutionException ignored) {
            return null;
        }
    }

    /**
     * Account info from a non-shortened username, or {@code null} if none was found.
     * @throws IllegalArgumentException if the username is empty or null
     */
    public Account getAccount(String fullName) {
        Preconditions.checkNotNull(fullName, "fullName cannot be null!");
        Preconditions.checkArgument(!fullName.isEmpty(), "fullName cannot be empty!");
        try {
            synchronized (accountsLock) {
                return usernameToAccount.get(fullName, () -> {
                    try {
                        Account account = repository.findLatestByName(fullName).orElse(null);
                        if (account != null) {
                            account.setName(fullName);
                            shortToAccount.put(account.getShortName(), account);
                            return account;
                        }
                    } catch (IllegalStateException e) {
                        ChestShop.getBukkitLogger().log(Level.WARNING, "Error while getting account for " + fullName + ":", e);
                    }
                    throw new Exception("Could not find account for " + fullName);
                });
            }
        } catch (ExecutionException ignored) {
            return null;
        }
    }

    /**
     * Account info from a username that might be shortened, or {@code null} if none was found.
     * @throws IllegalArgumentException if the username is empty
     */
    public Account getAccountFromShortName(String shortName) {
        Preconditions.checkNotNull(shortName, "shortName cannot be null!");
        Preconditions.checkArgument(!shortName.isEmpty(), "shortName cannot be empty!");
        Account account = null;

        try {
            synchronized (accountsLock) {
                account = shortToAccount.get(shortName, () -> {
                    try {
                        Account a = repository.findByShortName(shortName).orElse(null);
                        if (a != null) {
                            a.setShortName(shortName);
                            return a;
                        }
                    } catch (IllegalStateException e) {
                        ChestShop.getBukkitLogger().log(Level.WARNING, "Error while getting account for " + shortName + ":", e);
                    }
                    throw new Exception("Could not find account for " + shortName);
                });
            }
        } catch (ExecutionException ignored) {}
        return account;
    }

    /** Resolve an {@link AccountQueryEvent} (entrypoint: {@code NameManager.onAccountQuery}). */
    public void onAccountQuery(AccountQueryEvent event) {
        if (event.getAccount() == null) {
            event.setAccount(getLastAccountFromName(event.getName(), event.searchOfflinePlayers()));
        }
    }

    /**
     * The information from the last time a player logged in that previously used the (shortened) name.
     * @throws IllegalArgumentException if the username is empty
     */
    private Account getLastAccountFromName(String name, boolean searchOfflinePlayer) {
        Account account = getAccountFromShortName(name); // first get the account associated with the short name
        if (account == null) {
            account = getAccount(name);
        }
        if (account == null && searchOfflinePlayer && !invalidPlayers.contains(name.toLowerCase(Locale.ROOT))) {
            // no account with that shortname was found, try to get an offline player with that name
            OfflinePlayer offlinePlayer = ChestShop.getBukkitServer().getOfflinePlayer(name);
            if (offlinePlayer != null && offlinePlayer.getName() != null && offlinePlayer.getUniqueId() != null
                    && (!Properties.ENSURE_CORRECT_PLAYERID || offlinePlayer.getUniqueId().version() == uuidVersion)) {
                account = storeUsername(new PlayerDTO(offlinePlayer.getUniqueId(), offlinePlayer.getName()));
            } else {
                invalidPlayers.put(name.toLowerCase(Locale.ROOT), true);
            }
        }
        if (account != null) {
            return getAccount(account.getUuid()); // then get the last account that was online with that UUID
        }
        return null;
    }

    /**
     * Store the username of a player into the database and the username-uuid cache.
     * @return The stored/updated account, or {@code null} if there was an error updating it
     */
    public Account storeUsername(final PlayerDTO player) {
        final UUID uuid = player.getUniqueId();

        Account latestAccount;
        synchronized (accountsLock) {
            try {
                latestAccount = repository.findByUuidAndName(uuid, player.getName()).orElse(null);
            } catch (IllegalStateException e) {
                ChestShop.getBukkitLogger().log(Level.WARNING, "Error while searching for latest account of " + player.getName() + "/" + uuid + ":", e);
                latestAccount = null;
            }

            if (latestAccount == null) {
                latestAccount = new Account(player.getName(), getNewShortenedName(player), player.getUniqueId());
            }

            latestAccount.setLastSeen(new Date());
            try {
                storeAccount(latestAccount);
            } catch (IllegalStateException e) {
                ChestShop.getBukkitLogger().log(Level.WARNING, "Error while updating account " + latestAccount + ":", e);
                return null;
            }

            usernameToAccount.put(latestAccount.getName(), latestAccount);
            uuidToAccount.put(uuid, latestAccount);
            shortToAccount.put(latestAccount.getShortName(), latestAccount);
        }

        return latestAccount;
    }

    /**
     * Store an account into the database.
     * @throws IllegalStateException if the backing store could not be written
     */
    public void storeAccount(Account account) {
        repository.save(account);
    }

    /**
     * Get a new unique shortened name that hasn't been used by another player yet
     * (a maximum of 15 chars long).
     */
    String getNewShortenedName(PlayerDTO player) {
        String shortenedName = NameUtil.stripUsername(player.getName());

        Account account = getAccountFromShortName(shortenedName);
        if (account == null) {
            return shortenedName;
        }
        for (int id = 0; account != null; id++) {
            String baseId = Base62.encode(id);
            shortenedName = NameUtil.stripUsername(player.getName(), 15 - 1 - baseId.length()) + ":" + baseId;
            account = getAccountFromShortName(shortenedName);
        }

        return shortenedName;
    }

    public boolean canUseName(Player player, Permission base, String name) {
        if (ChestShopSign.isAdminShop(name)) {
            if (Permission.has(player, Permission.ADMIN_SHOP)) {
                return true;
            } else {
                ChestShop.logDebug(player.getName() + " cannot use the name " + name + " as it's an admin shop and they don't have the permission " + Permission.ADMIN_SHOP);
                return false;
            }
        }

        boolean isBusinessAccount = ChestShopSign.isBusinessAccount(name);

        // For business accounts, skip permission-based shortcuts — access is controlled
        // solely by Treasury via AccountAccessEvent. Only ChestShop admins bypass this.
        if (isBusinessAccount) {
            if (Permission.has(player, Permission.ADMIN)) {
                return true;
            }
        } else {
            if (Permission.otherName(player, base, name)) {
                return true;
            }
        }

        AccountQueryEvent queryEvent = new AccountQueryEvent(name);
        ChestShop.callEvent(queryEvent);
        Account account = queryEvent.getAccount();
        if (account == null) {
            // There is no account by the provided name, but it matches the player name
            // Return true as they specified their own name and a new account should get created
            if (player.getName().equalsIgnoreCase(name)) {
                return true;
            }
            ChestShop.logDebug(player.getName() + " cannot use the name " + name + " for a shop as no account with that name exists");
            return false;
        }
        if (!isBusinessAccount && !account.getName().equalsIgnoreCase(name) && Permission.otherName(player, base, account.getName())) {
            return true;
        }
        AccountAccessEvent event = new AccountAccessEvent(player, account);
        ChestShop.callEvent(event);
        return event.canAccess();
    }

    /** Default access rule (entrypoint: {@code NameManager.onAccountAccessCheck}): the player owns the account. */
    public void onAccountAccessCheck(AccountAccessEvent event) {
        if (!event.canAccess()) {
            event.setAccess(event.getPlayer().getUniqueId().equals(event.getAccount().getUuid()));
            if (!event.canAccess()) {
                ChestShop.logDebug(event.getPlayer().getName() + "/" + event.getPlayer().getUniqueId()
                        + " cannot access the account " + event.getAccount().getName() + "/" + event.getAccount().getUuid()
                        + " as their UUID doesn't match!");
            }
        }
    }

    public boolean isAdminShop(UUID uuid) {
        return adminAccount != null && uuid.equals(adminAccount.getUuid());
    }

    public boolean isServerEconomyAccount(UUID uuid) {
        return serverEconomyAccount != null && uuid.equals(serverEconomyAccount.getUuid());
    }

    public void load() {
        if (getUuidVersion() < 0) {
            if (Bukkit.getOnlineMode()) {
                setUuidVersion(4);
            } else if (!Bukkit.getOnlinePlayers().isEmpty()) {
                setUuidVersion(Bukkit.getOnlinePlayers().iterator().next().getUniqueId().version());
            }
        }
        try {
            try {
                adminAccount = new Account(Properties.ADMIN_SHOP_NAME, Bukkit.getOfflinePlayer(Properties.ADMIN_SHOP_NAME).getUniqueId());
            } catch (NullPointerException ratelimitedException) {
                // This happens when the server was ratelimited by Mojang. Unfortunately there is no nice way to check that.
                // We fall back to the method used by CraftBukkit to generate an OfflinePlayer's UUID
                adminAccount = new Account(Properties.ADMIN_SHOP_NAME, UUID.nameUUIDFromBytes(("OfflinePlayer:" + Properties.ADMIN_SHOP_NAME).getBytes(Charsets.UTF_8)));
                ChestShop.getBukkitLogger().log(Level.WARNING, "Your server appears to be ratelimited by Mojang and can't query UUID data from their API. If you run into issues with admin shops please report them!");
            }
            repository.save(adminAccount);

            if (!Properties.SERVER_ECONOMY_ACCOUNT.isEmpty()) {
                serverEconomyAccount = getAccount(Properties.SERVER_ECONOMY_ACCOUNT);
            }
            if (serverEconomyAccount == null && !Properties.SERVER_ECONOMY_ACCOUNT.isEmpty() && !Properties.SERVER_ECONOMY_ACCOUNT_UUID.equals(new UUID(0, 0))) {
                serverEconomyAccount = getOrCreateAccount(Properties.SERVER_ECONOMY_ACCOUNT_UUID, Properties.SERVER_ECONOMY_ACCOUNT);
            }
            if (serverEconomyAccount == null || serverEconomyAccount.getUuid() == null) {
                serverEconomyAccount = null;
                if (!Properties.SERVER_ECONOMY_ACCOUNT.isEmpty()) {
                    ChestShop.getBukkitLogger().log(Level.WARNING, "Server economy account setting '"
                            + Properties.SERVER_ECONOMY_ACCOUNT
                            + "' doesn't seem to be the name of a known player account!" +
                            " Please specify the SERVER_ECONOMY_ACCOUNT_UUID" +
                            " or log in at least once and create a player shop with that account" +
                            " in order for the server economy account to work.");
                }
            }
        } catch (IllegalStateException e) {
            ChestShop.getBukkitLogger().log(Level.SEVERE, "Error while trying to setup accounts", e);
        }
    }

    public Account getServerEconomyAccount() {
        return serverEconomyAccount;
    }

    public void setUuidVersion(int uuidVersion) {
        this.uuidVersion = uuidVersion;
    }

    public int getUuidVersion() {
        return uuidVersion;
    }
}
