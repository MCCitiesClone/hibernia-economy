package io.paradaux.chestshop.economy;

import io.paradaux.chestshop.ChestShop;
import io.paradaux.chestshop.services.BusinessAccountService;
import io.paradaux.chestshop.services.EconomyService;
import io.paradaux.chestshop.utils.BusinessAccountUtil;
import io.paradaux.business.api.BusinessApi;
import io.paradaux.treasury.api.TaxApi;
import io.paradaux.treasury.model.economy.AccountType;
import io.paradaux.treasury.api.TreasuryApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Treasury economy provider for ChestShop.
 *
 * <p>This provider is now only responsible for advertising the active provider
 * ({@link #getProviderInfo()}) and, at enable time, resolving the Treasury (and
 * optional Business) handles and binding them to {@link io.paradaux.chestshop.services.EconomyService}
 * via {@link #prepare(EconomyService)}. All ledger access — account resolution, access
 * checks, balances, settlement and legacy business-sign migration — runs through that
 * single {@link TreasuryApi}/{@link BusinessApi} boundary.
 */
public class TreasuryEconomyProvider extends EconomyProvider {


    /**
     * Attempt to initialize the Treasury economy provider.
     *
     * @return A new TreasuryEconomyProvider, or null if Treasury is not available
     */
    @Nullable
    public static TreasuryEconomyProvider prepare(EconomyService economy, BusinessAccountService businessAccounts) {
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
                    treasury.getAccountsByTypeAndOwner(AccountType.SYSTEM, BusinessAccountUtil.CHESTSHOP_SYSTEM_UUID);
            if (systemAccounts != null && !systemAccounts.isEmpty()) {
                systemAccountId = systemAccounts.get(0).getAccountId();
            } else {
                io.paradaux.treasury.model.economy.Account systemAccount =
                        treasury.createAccount(AccountType.SYSTEM, BusinessAccountUtil.CHESTSHOP_SYSTEM_UUID, "ChestShop System");
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
        economy.bind(treasury, systemAccountId, taxApi);
        businessAccounts.bind(treasury, businessApi);

        return new TreasuryEconomyProvider();
    }

    @Override
    @Nullable
    public ProviderInfo getProviderInfo() {
        return new ProviderInfo("Treasury", Bukkit.getPluginManager().getPlugin("Treasury").getDescription().getVersion());
    }
}
