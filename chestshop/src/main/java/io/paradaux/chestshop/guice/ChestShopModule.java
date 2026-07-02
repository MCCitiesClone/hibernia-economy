package io.paradaux.chestshop.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.BusinessAccountService;
import io.paradaux.chestshop.services.EconomyService;
import io.paradaux.chestshop.services.ItemCodeService;
import io.paradaux.chestshop.services.ShopService;
import io.paradaux.chestshop.services.TransactionService;
import io.paradaux.chestshop.signs.ChestShopSign;
import io.paradaux.chestshop.services.InventoryService;
import io.paradaux.chestshop.services.MaterialService;
import io.paradaux.chestshop.services.ShopBlockService;

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
        bind(BusinessAccountService.class).in(Singleton.class);
        // The stateful, config-backed material/inventory/block logic, split out of the
        // former static-util classes into @Singleton services routed through
        // ChestShopConfiguration (PAR-282). Bound explicitly so the single shared
        // instance (e.g. MaterialService's material cache) is unambiguous; the pure,
        // stateless helpers stay static on the utils/*Util classes. The dependency graph
        // is fully acyclic now — no Provider<> cycle-breakers (ShopBlockService depends on
        // ChestShopSign directly, since the block-detection predicates live on the service).
        bind(MaterialService.class).in(Singleton.class);
        bind(InventoryService.class).in(Singleton.class);
        bind(ShopBlockService.class).in(Singleton.class);
        bind(ChestShopSign.class).in(Singleton.class);
    }
}
