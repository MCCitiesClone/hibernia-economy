package io.paradaux.chestshop.adapters;

import org.jetbrains.annotations.Nullable;

/**
 * ChestShop's economy provider abstraction, exposing the active provider's
 * {@link ProviderInfo} for metrics. Treasury is the only implementation
 * ({@code TreasuryEconomyProvider}); the former currency-event handlers it declared
 * have all collapsed into {@link io.paradaux.chestshop.services.EconomyService}, so
 * this is no longer a Bukkit {@code Listener}.
 */
public abstract class EconomyProvider {

    @Nullable
    public abstract ProviderInfo getProviderInfo();

    public static class ProviderInfo {
        private final String name;
        private final String version;

        public ProviderInfo(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }
    }
}
