package io.paradaux.treasury.adapters;

import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.Treasury;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

/**
 * Registers {@link VaultEconomyAdapter} as the Bukkit Vault economy provider.
 *
 * <p>Isolated in its own class so that {@link Treasury} contains no symbolic
 * references to Vault types. This file is only loaded when Vault is on the
 * classpath, gated by {@code Treasury#isVaultAvailable()}.
 */
@Slf4j
public final class VaultEconomyRegistrar {

    private VaultEconomyRegistrar() {}

    public static void register(Treasury plugin, Injector injector) {
        VaultEconomyAdapter adapter = injector.getInstance(VaultEconomyAdapter.class);

        RegisteredServiceProvider<Economy> existing =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (existing != null) {
            log.info("Replacing existing Vault economy provider: {}",
                    existing.getPlugin().getName());
            Bukkit.getServicesManager().unregister(existing.getProvider());
        }

        Bukkit.getServicesManager().register(Economy.class, adapter, plugin, ServicePriority.Highest);
        log.info("Vault economy provider registered.");
    }
}
