package io.paradaux.business.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.configurator.ConfigurationLoader;
import io.paradaux.hibernia.framework.i18n.Message;
import io.paradaux.business.Business;

import io.paradaux.business.api.BusinessApi;
import io.paradaux.business.api.impl.BusinessApiImpl;
import io.paradaux.business.jobs.ExpireRequestsJob;
import io.paradaux.business.listeners.FirmBalanceTaxListener;
import io.paradaux.business.model.config.BalanceTaxConfiguration;
import io.paradaux.business.model.config.FirmConfiguration;
import io.paradaux.business.services.*;
import io.paradaux.business.services.impl.*;
import io.paradaux.treasury.api.TreasuryApi;

import java.util.Map;

public class BusinessModule extends AbstractModule {

    private final Business business;
    private final ConfigurationLoader configurationLoader;
    private final TreasuryApi treasuryApi;

    public BusinessModule(Business business, ConfigurationLoader configurationLoader, TreasuryApi treasuryApi) {
        this.business = business;
        this.configurationLoader = configurationLoader;
        this.treasuryApi = treasuryApi;
    }

    @Override
    protected void configure() {
        bind(Business.class).toInstance(business);
        bind(ConfigurationLoader.class).toInstance(configurationLoader);
        bind(TreasuryApi.class).toInstance(treasuryApi);

        // Automatically bind all configuration components
        for (Map.Entry<Class<?>, Object> entry : configurationLoader.getComponents().entrySet()) {
            @SuppressWarnings("unchecked")
            Class<Object> key = (Class<Object>) entry.getKey();
            Object value = entry.getValue();
            bind(key).toInstance(value);
        }

        // Framework Beans
        bind(Message.class).asEagerSingleton();

        // Bind services
        bind(FirmAccountService.class).to(FirmAccountServiceImpl.class).in(Singleton.class);
        bind(FirmAreaShopService.class).to(FirmAreaShopServiceImpl.class).in(Singleton.class);
        bind(FirmRoleService.class).to(FirmRoleServiceImpl.class).in(Singleton.class);
        bind(FirmService.class).to(FirmServiceImpl.class).in(Singleton.class);
        bind(FirmStaffService.class).to(FirmStaffServiceImpl.class).in(Singleton.class);
        bind(FirmTransactionService.class).to(FirmTransactionServiceImpl.class).in(Singleton.class);
        bind(FirmNotificationService.class).to(FirmNotificationServiceImpl.class).in(Singleton.class);
        bind(FirmDisbandConfirmationService.class).to(FirmDisbandConfirmationServiceImpl.class).in(Singleton.class);
        bind(FirmRequestService.class).to(FirmRequestServiceImpl.class).in(Singleton.class);
        bind(FirmPlayerService.class).to(FirmPlayerServiceImpl.class).in(Singleton.class);
        bind(FirmPropertyService.class).to(FirmPropertyServiceImpl.class).in(Singleton.class);
        bind(FirmBalanceTaxService.class).to(FirmBalanceTaxServiceImpl.class).in(Singleton.class);

        // Configuration (reads from plugin config, not ConfigurationLoader)
        bind(BalanceTaxConfiguration.class).in(Singleton.class);
        bind(FirmConfiguration.class).in(Singleton.class);

        // Bind API
        bind(BusinessApi.class).to(BusinessApiImpl.class).in(Singleton.class);

        // Bind Jobs
        bind(ExpireRequestsJob.class).in(Singleton.class);

        // Bind Listeners
        bind(FirmBalanceTaxListener.class).in(Singleton.class);
    }
}