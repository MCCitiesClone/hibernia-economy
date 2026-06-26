package io.paradaux.chestshop.listeners.economy.plugins;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.events.AccountAccessEvent;
import io.paradaux.chestshop.events.AccountQueryEvent;
import io.paradaux.chestshop.events.TransactionEvent;
import io.paradaux.chestshop.listeners.economy.EconomyAdapter;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.business.api.BusinessApi;
import io.paradaux.business.model.RolePermission;
import io.paradaux.treasury.api.TaxApi;
import io.paradaux.treasury.model.economy.AccountType;
import io.paradaux.treasury.api.TreasuryApi;
import org.bukkit.Bukkit;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.RegisteredServiceProvider;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Treasury economy adapter for ChestShop.
 * Supports both personal accounts (via player UUID) and business accounts (via synthetic UUIDs).
 */
public class TreasuryListener extends EconomyAdapter {

    static final long BUSINESS_UUID_MSB = 0xC5B0000000000000L;
    static final UUID CHESTSHOP_SYSTEM_UUID = new UUID(0xC5B0FFFFFFFFFFFEL, 0xFFFFFFFFFFFFFFFEL);

    private final TreasuryApi treasury;
    @Nullable private final BusinessApi businessApi;

    private TreasuryListener(TreasuryApi treasury, @Nullable BusinessApi businessApi) {
        this.treasury = treasury;
        this.businessApi = businessApi;
    }

    /**
     * Attempt to initialize the Treasury listener.
     *
     * @return A new TreasuryListener, or null if Treasury is not available
     */
    @Nullable
    public static TreasuryListener prepareListener() {
        if (Bukkit.getPluginManager().getPlugin("Treasury") == null) {
            return null;
        }

        RegisteredServiceProvider<TreasuryApi> rsp =
                Bukkit.getServicesManager().getRegistration(TreasuryApi.class);
        if (rsp == null) {
            ChestShop.getBukkitLogger().warning("Treasury plugin found but TreasuryApi service not registered!");
            return null;
        }

        TreasuryApi treasury = rsp.getProvider();

        // Find or create the ChestShop SYSTEM account for intermediary transfers
        int systemAccountId;
        try {
            List<io.paradaux.treasury.model.economy.Account> systemAccounts =
                    treasury.getAccountsByTypeAndOwner(AccountType.SYSTEM, CHESTSHOP_SYSTEM_UUID);
            if (systemAccounts != null && !systemAccounts.isEmpty()) {
                systemAccountId = systemAccounts.get(0).getAccountId();
            } else {
                io.paradaux.treasury.model.economy.Account systemAccount =
                        treasury.createAccount(AccountType.SYSTEM, CHESTSHOP_SYSTEM_UUID, "ChestShop System");
                systemAccount.setAllowOverdraft(true);
                treasury.updateAccount(systemAccount);
                systemAccountId = systemAccount.getAccountId();
            }
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.SEVERE, "Failed to initialize Treasury SYSTEM account!", e);
            return null;
        }

        ChestShop.getBukkitLogger().info("Treasury SYSTEM account initialized (ID: " + systemAccountId + ")");

        // Resolve TaxApi for sales-tax routing into Treasury's default tax
        // account (typically DCGovernment). Treasury exposes it as a separate
        // service so we don't need a second Bukkit lookup.
        TaxApi taxApi = treasury.getTaxApi();
        if (taxApi == null) {
            ChestShop.getBukkitLogger().warning(
                    "Treasury loaded but TaxApi unavailable — ChestShop sales tax will not be collected.");
        }
        ChestShop.getBukkitLogger().info("Sales tax now routed via Treasury TaxApi → "
                + (taxApi != null ? taxApi.getDefaultTaxAccountName() : "(disabled)"));

        // Hand the live ledger handle + SYSTEM account + tax API to the EconomyService —
        // ChestShop's direct TreasuryApi boundary that replaced the currency event bus.
        ChestShop.economy().bind(treasury, systemAccountId, taxApi);

        // Optionally integrate with the Business plugin for CHESTSHOP permission checks
        BusinessApi businessApi = null;
        if (Bukkit.getPluginManager().getPlugin("Business") != null) {
            RegisteredServiceProvider<BusinessApi> businessRsp =
                    Bukkit.getServicesManager().getRegistration(BusinessApi.class);
            if (businessRsp != null) {
                businessApi = businessRsp.getProvider();
                ChestShop.getBukkitLogger().info("Business API integrated: firm CHESTSHOP permissions will gate shop access.");
            } else {
                ChestShop.getBukkitLogger().warning("Business plugin found but BusinessApi service not registered — falling back to Treasury membership checks.");
            }
        }

        return new TreasuryListener(treasury, businessApi);
    }

    @Override
    @Nullable
    public ProviderInfo getProviderInfo() {
        return new ProviderInfo("Treasury", Bukkit.getPluginManager().getPlugin("Treasury").getDescription().getVersion());
    }

    // --- Synthetic UUID helpers ---

    static boolean isBusinessUuid(UUID uuid) {
        return uuid.getMostSignificantBits() == BUSINESS_UUID_MSB;
    }

    static UUID toBusinessUuid(int accountId) {
        return new UUID(BUSINESS_UUID_MSB, (long) accountId);
    }

    /**
     * Resolve a UUID to a Treasury account ID.
     * If the UUID is a synthetic business UUID, extract the account ID directly.
     * Otherwise, prefer a GOVERNMENT account owned by that UUID before falling
     * back to resolving (or creating) a personal account for the player UUID.
     */
    private int resolveAccountId(UUID uuid) {
        if (isBusinessUuid(uuid)) {
            return (int) uuid.getLeastSignificantBits();
        }
        Integer governmentAccountId = resolveGovernmentAccountId(uuid);
        if (governmentAccountId != null) {
            return governmentAccountId;
        }
        io.paradaux.treasury.model.economy.Account account = treasury.resolveOrCreatePersonal(uuid);
        return account.getAccountId();
    }

    /**
     * Legacy DemocracyCraft government ledgers (DCGovernment, SCGovernment, ...) used to
     * be real players, so shops still address them by that player's name/UUID. Those
     * ledgers are now GOVERNMENT accounts whose {@code owner_uuid_bin} has been set to the
     * legacy player UUID, so they must take precedence over personal resolution. Otherwise
     * {@link TreasuryApi#resolveOrCreatePersonal(UUID)} — which filters to PERSONAL type —
     * would mint an empty personal account and shop funds would never reach the ledger.
     *
     * @return the GOVERNMENT account id owned by {@code uuid}, or {@code null} if none exists.
     */
    private Integer resolveGovernmentAccountId(UUID uuid) {
        try {
            List<io.paradaux.treasury.model.economy.Account> governmentAccounts =
                    treasury.getAccountsByTypeAndOwner(AccountType.GOVERNMENT, uuid);
            if (governmentAccounts != null && !governmentAccounts.isEmpty()) {
                return governmentAccounts.get(0).getAccountId();
            }
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING,
                    "Treasury: government account lookup failed for " + uuid, e);
        }
        return null;
    }

    // --- Account query/access handlers for business accounts ---

    @EventHandler(priority = EventPriority.LOW)
    public void onAccountQuery(AccountQueryEvent event) {
        if (event.getAccount() != null) {
            return;
        }

        String name = event.getName();
        // A business token is anything starting with "B:" — the native, uppercase form
        // written by ChestShopSign.businessAccountSignName, or the legacy lowercase "b:"
        // form written by the old PlayerBusinesses/PlayerTreasury chestshops. We accept
        // both prefixes (and any suffix, incl. firm names with spaces) here; the suffix is
        // disambiguated below. Player names can never contain ':' so this never collides.
        if (name == null || name.length() < 3 || !name.regionMatches(true, 0, "B:", 0, 2)) {
            return;
        }

        try {
            String token = name.substring(2);
            int accountId = -1;
            io.paradaux.treasury.model.economy.Account treasuryAccount = null;

            // Native form: the suffix is a base-36 Treasury account id (e.g. B:1A).
            try {
                accountId = Integer.parseInt(token, 36);
                treasuryAccount = treasury.getAccountById(accountId);
            } catch (NumberFormatException notBase36) {
                // e.g. a legacy firm name containing spaces — fall through to the name lookup.
            }

            // Legacy migration form: the suffix is an old PlayerBusinesses firm *name*
            // (e.g. b:My Shop). Resolve it to the firm's default BUSINESS account so the
            // shop keeps working; the physical sign is rewritten to the native form on
            // first use (see onTransactionMigrateSign). This is also the fallback when a
            // firm name happens to be valid base-36 but doesn't decode to a real account.
            if (treasuryAccount == null && businessApi != null) {
                io.paradaux.business.model.Firm firm = businessApi.firms().getFirm(token);
                if (firm != null && firm.getDefaultAccountId() != null) {
                    accountId = firm.getDefaultAccountId();
                    treasuryAccount = treasury.getAccountById(accountId);
                }
            }

            if (treasuryAccount != null) {
                String displayName = treasuryAccount.getDisplayName();
                String shortName = ChestShopSign.businessAccountSignName(accountId);
                UUID syntheticUuid = toBusinessUuid(accountId);
                Account csAccount = new Account(displayName, shortName, syntheticUuid);
                event.setAccount(csAccount);
            }
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING, "Treasury: Could not resolve business account for " + name, e);
        }
    }

    /**
     * Lazily migrates legacy business shop signs to the native account-id format.
     *
     * <p>The old PlayerBusinesses chestshops addressed a firm by name
     * ({@code b:<FirmName>}); the native format is {@code B:<base36 account id>}
     * ({@link ChestShopSign#businessAccountSignName(int)}). By the time a shop
     * trades, {@link #onAccountQuery} has already resolved the owner account, and
     * its short name is the canonical native token. So if the physical sign still
     * shows the legacy text, we rewrite the owner line in place. This runs only on
     * a completed (non-cancelled) transaction and is a no-op for shops already in
     * the native form. Firms whose names were altered during the data migration
     * (stripped special characters) won't resolve and are intentionally left for
     * their owners to recreate.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransactionMigrateSign(TransactionEvent event) {
        Sign sign = event.getSign();
        Account owner = event.getOwnerAccount();
        if (sign == null || owner == null || owner.getUuid() == null || !isBusinessUuid(owner.getUuid())) {
            return;
        }

        String canonical = owner.getShortName();
        if (canonical == null || canonical.equals(ChestShopSign.getOwner(sign))) {
            return;
        }

        sign.setLine(ChestShopSign.NAME_LINE, canonical);
        sign.update(true);
        ChestShop.getBukkitLogger().info("Migrated legacy business shop sign to " + canonical
                + " at " + sign.getLocation());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAccountAccess(AccountAccessEvent event) {
        if (event.canAccess()) {
            return;
        }

        Account account = event.getAccount();
        if (account == null || account.getShortName() == null) {
            return;
        }

        String shortName = account.getShortName();
        if (!shortName.toUpperCase(Locale.ROOT).startsWith("B:")) {
            return;
        }

        UUID uuid = account.getUuid();
        if (!isBusinessUuid(uuid)) {
            return;
        }

        try {
            int accountId = (int) uuid.getLeastSignificantBits();
            UUID playerUuid = event.getPlayer().getUniqueId();

            if (businessApi != null) {
                // Business plugin is present: use the CHESTSHOP role-permission as the
                // authoritative gate for both shop creation and shop ownership checks.
                if (businessApi.staff().hasPermissionForAccount(accountId, playerUuid, RolePermission.CHESTSHOP)) {
                    event.setAccess(true);
                } else if (businessApi.firms().getFirmByAccountId(accountId) == null) {
                    // PAR-29: no live firm owns this account — the firm was disbanded
                    // (disband archives the account and removes its firm link). The shop
                    // is orphaned with no owner to gate it, so let players access (and
                    // therefore remove) the abandoned shop rather than leaving it stuck.
                    event.setAccess(true);
                }
            } else {
                // Business plugin absent: fall back to Treasury account membership.
                boolean canAccess = treasury.isAccountMember(playerUuid, accountId)
                        || treasury.isOwnerForAccountId(playerUuid, accountId);
                if (canAccess) {
                    event.setAccess(true);
                }
            }
        } catch (Exception e) {
            ChestShop.getBukkitLogger().log(Level.WARNING, "Treasury: Could not check access for " + event.getPlayer().getName() + " on account " + shortName, e);
        }
    }
}
