package io.paradaux.treasuryapi.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.configurator.ConfigurationLoader;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.business.api.BusinessApi;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasuryapi.TreasuryAPI;
import io.paradaux.treasuryapi.commands.BusinessKeyHandler;
import io.paradaux.treasuryapi.commands.PersonalKeyHandler;
import io.paradaux.treasuryapi.commands.UiAccessHandler;
import io.paradaux.treasuryapi.services.ApiKeyService;
import io.paradaux.treasuryapi.services.KeycloakAdminClient;
import io.paradaux.treasuryapi.services.impl.ApiKeyServiceImpl;

import java.util.Map;

public class TreasuryAPIModule extends AbstractModule {

    private final TreasuryAPI plugin;
    private final ConfigurationLoader configurationLoader;
    private final TreasuryApi treasuryApi;
    private final BusinessApi businessApi;

    public TreasuryAPIModule(TreasuryAPI plugin, ConfigurationLoader configurationLoader,
                             TreasuryApi treasuryApi, BusinessApi businessApi) {
        this.plugin = plugin;
        this.configurationLoader = configurationLoader;
        this.treasuryApi = treasuryApi;
        this.businessApi = businessApi;
    }

    @Override
    protected void configure() {
        bind(TreasuryAPI.class).toInstance(plugin);
        bind(ConfigurationLoader.class).toInstance(configurationLoader);
        bind(TreasuryApi.class).toInstance(treasuryApi);
        bind(BusinessApi.class).toInstance(businessApi);

        // Automatically bind all configuration components
        for (Map.Entry<Class<?>, Object> entry : configurationLoader.getComponents().entrySet()) {
            @SuppressWarnings("unchecked")
            Class<Object> key = (Class<Object>) entry.getKey();
            Object value = entry.getValue();
            bind(key).toInstance(value);
        }

        // Framework beans
        bind(Message.class).asEagerSingleton();

        // Services
        bind(ApiKeyService.class).to(ApiKeyServiceImpl.class).in(Singleton.class);

        // Services
        bind(KeycloakAdminClient.class).in(Singleton.class);

        // Command handlers
        bind(PersonalKeyHandler.class).in(Singleton.class);
        bind(BusinessKeyHandler.class).in(Singleton.class);
        bind(UiAccessHandler.class).in(Singleton.class);

        // LuckPerms (optional softdepend) for the group reconciliation cron. Guard
        // the class reference behind Class.forName so the bare LuckPerms.class
        // literal never throws NoClassDefFoundError when LuckPerms is absent.
        if (isClassPresent("net.luckperms.api.LuckPerms")) {
            net.luckperms.api.LuckPerms lp =
                    plugin.getServer().getServicesManager().load(net.luckperms.api.LuckPerms.class);
            if (lp != null) {
                bind(net.luckperms.api.LuckPerms.class).toInstance(lp);
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
