package io.paradaux.chestshop.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.EconomyService;
import io.paradaux.chestshop.services.ItemCodeService;
import io.paradaux.chestshop.services.ShopService;
import io.paradaux.chestshop.services.TransactionService;

/**
 * ChestShop's own Guice bindings — its service layer, alongside the framework's
 * {@code HiberniaModule} and the {@link DatabaseModule} that wires the MyBatis mappers.
 * This is the seam the plugin migrated onto: thin entrypoints → {@code services/}
 * (business logic) → {@code mappers/} (MyBatis persistence), replacing the static
 * God-classes and the internal event-bus pipelines. The mappers run over SQLite (as
 * before) but through MyBatis, the same service→mapper shape the other plugins use
 * (PAR-282).
 */
public class ChestShopModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ItemCodeService.class).in(Singleton.class);
        bind(TransactionService.class).in(Singleton.class);
        bind(AccountService.class).in(Singleton.class);
        bind(ShopService.class).in(Singleton.class);
        bind(EconomyService.class).in(Singleton.class);
    }
}
