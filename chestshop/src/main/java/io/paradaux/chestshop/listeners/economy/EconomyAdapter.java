package io.paradaux.chestshop.listeners.economy;

import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

/**
 * The economy provider abstraction: the {@link Listener} ChestShop registers to
 * settle its economy, exposing its {@link ProviderInfo} for metrics. Treasury is
 * the only implementation ({@code TreasuryListener}); the former currency-event
 * handlers it declared have all collapsed into {@link io.paradaux.chestshop.services.EconomyService}.
 */
public abstract class EconomyAdapter implements Listener {

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
