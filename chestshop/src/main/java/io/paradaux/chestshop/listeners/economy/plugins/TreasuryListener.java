package io.paradaux.chestshop.listeners.economy.plugins;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.database.Account;
import io.paradaux.chestshop.events.TransactionEvent;
import io.paradaux.chestshop.listeners.economy.EconomyAdapter;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.business.api.BusinessApi;
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
import java.util.UUID;
import java.util.logging.Level;

/**
 * Treasury economy adapter for ChestShop.
 *
 * <p>This adapter is now only responsible for two things: advertising the active
 * provider ({@link #getProviderInfo()}) and lazily migrating legacy business shop
 * signs to the native account-id format ({@link #onTransactionMigrateSign}). All
 * ledger access — account resolution, access checks, balances and settlement — runs
 * through {@link io.paradaux.chestshop.services.EconomyService}, the single
 * {@link TreasuryApi}/{@link BusinessApi} boundary that {@link #prepareListener()}
 * binds at enable time.
 */
public class TreasuryListener extends EconomyAdapter {

    static final long BUSINESS_UUID_MSB = 0xC5B0000000000000L;
    static final UUID CHESTSHOP_SYSTEM_UUID = new UUID(0xC5B0FFFFFFFFFFFEL, 0xFFFFFFFFFFFFFFFEL);

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

        // Hand the live ledger handle + SYSTEM account + tax/business APIs to the
        // EconomyService — ChestShop's direct TreasuryApi/BusinessApi boundary that
        // replaced the currency + account event bus.
        ChestShop.economy().bind(treasury, systemAccountId, taxApi, businessApi);

        return new TreasuryListener();
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

    /**
     * Lazily migrates legacy business shop signs to the native account-id format.
     *
     * <p>The old PlayerBusinesses chestshops addressed a firm by name
     * ({@code b:<FirmName>}); the native format is {@code B:<base36 account id>}
     * ({@link ChestShopSign#businessAccountSignName(int)}). By the time a shop
     * trades, {@link io.paradaux.chestshop.services.AccountService#resolveAccount(String)}
     * has already resolved the owner account, and its short name is the canonical
     * native token. So if the physical sign still shows the legacy text, we rewrite
     * the owner line in place. This runs only on a completed (non-cancelled)
     * transaction and is a no-op for shops already in the native form. Firms whose
     * names were altered during the data migration (stripped special characters)
     * won't resolve and are intentionally left for their owners to recreate.</p>
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
}
