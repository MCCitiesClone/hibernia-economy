package io.paradaux.treasuryapi.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.paradaux.business.api.BusinessApi;
import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasuryapi.commands.BusinessKeyHandler;
import io.paradaux.treasuryapi.commands.PersonalKeyHandler;
import io.paradaux.treasuryapi.commands.UiAccessHandler;
import io.paradaux.treasuryapi.services.ApiKeyService;
import io.paradaux.treasuryapi.services.KeycloakAdminClient;
import io.paradaux.treasuryapi.services.impl.ApiKeyServiceImpl;

/**
 * Plugin-specific bindings that {@link io.paradaux.hibernia.framework.guice.HiberniaModule}
 * does not provide. HiberniaModule already binds {@code TreasuryAPI}/{@code JavaPlugin}/
 * {@code Plugin}, the {@link io.paradaux.hibernia.framework.configurator.ConfigurationLoader},
 * every discovered {@code @ConfigurationComponent} (as a {@code toInstance} singleton),
 * {@link io.paradaux.hibernia.framework.i18n.Message} (eager), the command/resolver/listener
 * multibinders and PlaceholderAPI support — so this module must NOT re-bind any of those.
 *
 * <p>Reload note: there is no config-reload path today. HiberniaModule captures each config
 * component as a {@code toInstance} singleton, so if a {@code ConfigurationLoader.reload()} path
 * is ever added under framework 1.2.0, those captured instances (and any singleton service that
 * snapshotted their values) would go stale. A reload would then need to re-fetch each component
 * via {@code ConfigurationLoader.getComponent(...)} and re-issue the affected bindings. No reload
 * command is added here.</p>
 */
public class TreasuryAPIModule extends AbstractModule {

    private final TreasuryApi treasuryApi;
    private final BusinessApi businessApi;

    public TreasuryAPIModule(TreasuryApi treasuryApi, BusinessApi businessApi) {
        this.treasuryApi = treasuryApi;
        this.businessApi = businessApi;
    }

    @Override
    protected void configure() {
        // Economy APIs obtained from Bukkit services (Treasury/Business load first).
        bind(TreasuryApi.class).toInstance(treasuryApi);
        bind(BusinessApi.class).toInstance(businessApi);

        // Services
        bind(ApiKeyService.class).to(ApiKeyServiceImpl.class).in(Singleton.class);
        bind(KeycloakAdminClient.class).in(Singleton.class);

        // Command handlers
        bind(PersonalKeyHandler.class).in(Singleton.class);
        bind(BusinessKeyHandler.class).in(Singleton.class);
        bind(UiAccessHandler.class).in(Singleton.class);

        // LuckPerms (optional softdepend) for the group reconciliation cron. Guard
        // the class reference behind Class.forName so the bare LuckPerms.class
        // literal never throws NoClassDefFoundError when LuckPerms is absent.
        if (isClassPresent("net.luckperms.api.LuckPerms")) {
            bindLuckPerms();
        }
    }

    private void bindLuckPerms() {
        net.luckperms.api.LuckPerms lp =
                org.bukkit.Bukkit.getServicesManager().load(net.luckperms.api.LuckPerms.class);
        if (lp != null) {
            bind(net.luckperms.api.LuckPerms.class).toInstance(lp);
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
