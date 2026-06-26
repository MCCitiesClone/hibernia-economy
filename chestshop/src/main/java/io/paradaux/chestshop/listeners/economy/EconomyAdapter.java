package io.paradaux.chestshop.listeners.economy;

import io.paradaux.chestshop.events.economy.AccountCheckEvent;
import io.paradaux.chestshop.events.economy.CurrencyTransferEvent;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

public abstract class EconomyAdapter implements Listener {

    @Nullable
    public abstract ProviderInfo getProviderInfo();

    public abstract void onAccountCheck(AccountCheckEvent event);

    public abstract void onCurrencyTransfer(CurrencyTransferEvent event);

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
