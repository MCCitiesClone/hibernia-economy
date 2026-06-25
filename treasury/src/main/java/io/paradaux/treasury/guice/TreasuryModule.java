package io.paradaux.treasury.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.configurator.ConfigurationLoader;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.treasury.Treasury;
import io.paradaux.treasury.api.MarketApi;
import io.paradaux.treasury.api.SalesQueryApi;
import io.paradaux.treasury.api.TaxApi;
import io.paradaux.treasury.api.impl.MarketApiImpl;
import io.paradaux.treasury.api.impl.SalesQueryApiImpl;
import io.paradaux.treasury.api.impl.TaxApiImpl;
import io.paradaux.treasury.model.config.BalanceTaxConfiguration;
import io.paradaux.treasury.model.config.SalaryConfiguration;
import io.paradaux.treasury.model.config.SourceIncomeTaxConfiguration;
import io.paradaux.treasury.services.*;
import io.paradaux.treasury.services.impl.*;
import io.paradaux.treasury.utils.AccountRedirectCache;
import io.paradaux.treasury.utils.PersonalAccountCache;
import io.paradaux.treasury.utils.PluginSystemAccountCache;
import net.luckperms.api.LuckPerms;
import org.bukkit.Server;

import java.util.Map;

public class TreasuryModule extends AbstractModule {

    private final Treasury treasury;
    private final ConfigurationLoader configurationLoader;

    public TreasuryModule(Treasury treasury, ConfigurationLoader configurationLoader) {
        this.treasury = treasury;
        this.configurationLoader = configurationLoader;
    }

    @Override
    protected void configure() {
        bind(Treasury.class).toInstance(treasury);
        bind(ConfigurationLoader.class).toInstance(configurationLoader);

        // Automatically bind all configuration components
        for (Map.Entry<Class<?>, Object> entry : configurationLoader.getComponents().entrySet()) {
            @SuppressWarnings("unchecked")
            Class<Object> key = (Class<Object>) entry.getKey();
            bind(key).toInstance(entry.getValue());
        }

        // Framework Beans
        bind(Message.class).asEagerSingleton();

        // Utilities
        bind(PluginSystemAccountCache.class).in(Singleton.class);
        bind(AccountRedirectCache.class).in(Singleton.class);
        bind(PersonalAccountCache.class).in(Singleton.class);

        // Domain services
        bind(AccountService.class).to(AccountServiceImpl.class).in(Singleton.class);
        bind(MembershipService.class).to(MembershipServiceImpl.class).in(Singleton.class);
        bind(LedgerService.class).to(LedgerServiceImpl.class).in(Singleton.class);
        bind(BytebinService.class).to(BytebinServiceImpl.class).in(Singleton.class);
        bind(DataExportService.class).to(DataExportServiceImpl.class).in(Singleton.class);
        bind(GovService.class).to(GovServiceImpl.class).in(Singleton.class);
        bind(FineWebhookService.class).to(FineWebhookServiceImpl.class).in(Singleton.class);
        bind(PlayerDirectoryService.class).to(PlayerDirectoryServiceImpl.class).in(Singleton.class);
        bind(AuditService.class).to(AuditServiceImpl.class).in(Singleton.class);

        // Player-facing notifications for automated money movement (tax, salaries).
        bind(EconomyNotifier.class).to(EconomyNotifierImpl.class).in(Singleton.class);

        // Tax API
        bind(SourceIncomeTaxConfiguration.class).in(Singleton.class);
        bind(BalanceTaxConfiguration.class).in(Singleton.class);
        bind(BalanceTaxService.class).in(Singleton.class);
        bind(TaxCycleRegistry.class).in(Singleton.class);
        bind(TaxApiImpl.class).in(Singleton.class);
        bind(TaxApi.class).to(TaxApiImpl.class).in(Singleton.class);
        bind(TaxWebhookService.class).to(TaxWebhookServiceImpl.class).in(Singleton.class);

        // ChestShop market data (sales tracker + live shop registry)
        bind(MarketApiImpl.class).in(Singleton.class);
        bind(MarketApi.class).to(MarketApiImpl.class).in(Singleton.class);
        bind(SalesQueryApiImpl.class).in(Singleton.class);
        bind(SalesQueryApi.class).to(SalesQueryApiImpl.class).in(Singleton.class);

        // Government salaries — LuckPerms-group-based payouts on a timer.
        bind(Server.class).toInstance(treasury.getServer());
        bind(SalaryConfiguration.class).in(Singleton.class);
        bind(SalaryService.class).to(SalaryServiceImpl.class).in(Singleton.class);

        // LuckPerms (optional softdepend). Guard the class reference behind a
        // Class.forName check — like the Vault adapter — so the JVM never has to
        // resolve net.luckperms.api.LuckPerms when the plugin isn't installed.
        // Without this guard the bare LuckPerms.class literal below throws
        // NoClassDefFoundError at enable when LuckPerms is absent, disabling
        // Treasury (and every plugin that depends on it). MembershipService and
        // SalaryService already inject LuckPerms via @Inject(optional = true).
        if (isClassPresent("net.luckperms.api.LuckPerms")) {
            LuckPerms lp = treasury.getServer().getServicesManager().load(LuckPerms.class);
            if (lp != null) {
                bind(LuckPerms.class).toInstance(lp);
            }
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
