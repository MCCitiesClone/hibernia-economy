package io.paradaux.business.guice;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import io.paradaux.business.commands.*;

import io.paradaux.business.utils.resolvers.FirmNameResolver;
import io.paradaux.business.utils.resolvers.FirmPlayerResolver;
import io.paradaux.business.utils.resolvers.OnlineFirmNameResolver;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommanderModule extends AbstractModule {

    private final JavaPlugin plugin;

    public CommanderModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        // Bind both JavaPlugin and Plugin to the running plugin instance
        bind(JavaPlugin.class).toInstance(plugin);
        bind(Plugin.class).toInstance(plugin);

        Multibinder<CommandHandler> handlerBinder =
                Multibinder.newSetBinder(binder(), CommandHandler.class);

        Multibinder<ParameterResolver<?>> prm =
                Multibinder.newSetBinder(binder(), new TypeLiteral<>() {});
        prm.addBinding().to(FirmPlayerResolver.class);
        prm.addBinding().to(FirmNameResolver.class);
        prm.addBinding().to(OnlineFirmNameResolver.class);

        handlerBinder.addBinding().to(AccountCommands.class);
        handlerBinder.addBinding().to(FirmCommands.class);
        handlerBinder.addBinding().to(HelpCommands.class);
        handlerBinder.addBinding().to(MiscCommands.class);
        handlerBinder.addBinding().to(RequestCommands.class);
        handlerBinder.addBinding().to(RoleCommands.class);
        handlerBinder.addBinding().to(StaffCommands.class);
        handlerBinder.addBinding().to(ReloadCommand.class);
        handlerBinder.addBinding().to(TaxCommands.class);
        handlerBinder.addBinding().to(SalesCommands.class);
        handlerBinder.addBinding().to(ChatCommands.class);
    }
}
