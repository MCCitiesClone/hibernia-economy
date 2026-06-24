package io.paradaux.treasury.guice;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import io.paradaux.treasury.commands.*;
import io.paradaux.treasury.commands.resolvers.PayTargetResolver;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommanderModule extends AbstractModule {

    private final JavaPlugin plugin;

    public CommanderModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(JavaPlugin.class).toInstance(plugin);
        bind(Plugin.class).toInstance(plugin);

        Multibinder<CommandHandler> handlerBinder =
                Multibinder.newSetBinder(binder(), CommandHandler.class);
        handlerBinder.addBinding().to(TreasuryCommand.class);
        handlerBinder.addBinding().to(PayCommand.class);
        handlerBinder.addBinding().to(PayAccountCommand.class);
        handlerBinder.addBinding().to(BalanceCommand.class);
        handlerBinder.addBinding().to(BaltopCommand.class);
        handlerBinder.addBinding().to(EconomyCommand.class);
        handlerBinder.addBinding().to(SalesCommand.class);
        handlerBinder.addBinding().to(TransactionsCommand.class);
        handlerBinder.addBinding().to(EcoCommand.class);
        handlerBinder.addBinding().to(GovCommand.class);
        handlerBinder.addBinding().to(FineCommand.class);
        handlerBinder.addBinding().to(TaxCommand.class);

        Multibinder<ParameterResolver<?>> resolvers =
                Multibinder.newSetBinder(binder(), new TypeLiteral<>() {});
        resolvers.addBinding().to(PayTargetResolver.class);
    }
}
