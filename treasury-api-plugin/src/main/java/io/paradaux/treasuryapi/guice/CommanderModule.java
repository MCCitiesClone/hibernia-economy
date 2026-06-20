package io.paradaux.treasuryapi.guice;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import io.paradaux.treasuryapi.commands.TreasuryAPICommand;
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
        Multibinder.newSetBinder(binder(), new TypeLiteral<ParameterResolver<?>>() {});

        handlerBinder.addBinding().to(TreasuryAPICommand.class);
    }
}
