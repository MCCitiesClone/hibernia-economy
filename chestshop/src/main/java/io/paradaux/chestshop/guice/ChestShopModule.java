package io.paradaux.chestshop.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.paradaux.chestshop.services.AccountService;
import io.paradaux.chestshop.services.AdminBypass;
import io.paradaux.chestshop.services.BusinessAccountService;
import io.paradaux.chestshop.services.ChestShopSign;
import io.paradaux.chestshop.services.EconomyService;
import io.paradaux.chestshop.services.InfoService;
import io.paradaux.chestshop.services.InventoryService;
import io.paradaux.chestshop.services.ItemCodeService;
import io.paradaux.chestshop.services.ItemService;
import io.paradaux.chestshop.services.MarketResyncService;
import io.paradaux.chestshop.services.MaterialService;
import io.paradaux.chestshop.services.PreviewHandler;
import io.paradaux.chestshop.services.ProtectionService;
import io.paradaux.chestshop.services.Security;
import io.paradaux.chestshop.services.ShopBlockService;
import io.paradaux.chestshop.services.ShopFinderService;
import io.paradaux.chestshop.services.ShopService;
import io.paradaux.chestshop.services.TransactionService;
import io.paradaux.chestshop.services.impl.AccountServiceImpl;
import io.paradaux.chestshop.services.impl.AdminBypassImpl;
import io.paradaux.chestshop.services.impl.BusinessAccountServiceImpl;
import io.paradaux.chestshop.services.impl.EconomyServiceImpl;
import io.paradaux.chestshop.services.impl.InfoServiceImpl;
import io.paradaux.chestshop.services.impl.InventoryServiceImpl;
import io.paradaux.chestshop.services.impl.ItemCodeServiceImpl;
import io.paradaux.chestshop.services.impl.ItemServiceImpl;
import io.paradaux.chestshop.services.impl.MarketResyncServiceImpl;
import io.paradaux.chestshop.services.impl.MaterialServiceImpl;
import io.paradaux.chestshop.services.impl.PreviewHandlerImpl;
import io.paradaux.chestshop.services.impl.ProtectionServiceImpl;
import io.paradaux.chestshop.services.impl.SecurityImpl;
import io.paradaux.chestshop.services.impl.ShopBlockServiceImpl;
import io.paradaux.chestshop.services.impl.ShopFinderServiceImpl;
import io.paradaux.chestshop.services.impl.ShopServiceImpl;
import io.paradaux.chestshop.services.impl.TransactionServiceImpl;

/**
 * ChestShop's own Guice bindings — its service layer, alongside the framework's
 * {@code HiberniaModule} and the {@link DatabaseModule} that wires the MyBatis mappers.
 * This is the seam the plugin migrated onto: thin entrypoints → {@code services/}
 * (business logic) → {@code mappers/} (MyBatis persistence), replacing the static
 * God-classes and the internal event-bus pipelines. Each service is a {@code services/}
 * interface bound to its {@code services/impl/} implementation (PAR-306); the mappers run
 * over SQLite through MyBatis, the same service→mapper shape the other plugins use (PAR-282).
 */
public class ChestShopModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AccountService.class).to(AccountServiceImpl.class).in(Singleton.class);
        bind(AdminBypass.class).to(AdminBypassImpl.class).in(Singleton.class);
        bind(BusinessAccountService.class).to(BusinessAccountServiceImpl.class).in(Singleton.class);
        bind(EconomyService.class).to(EconomyServiceImpl.class).in(Singleton.class);
        bind(InfoService.class).to(InfoServiceImpl.class).in(Singleton.class);
        bind(InventoryService.class).to(InventoryServiceImpl.class).in(Singleton.class);
        bind(ItemCodeService.class).to(ItemCodeServiceImpl.class).in(Singleton.class);
        bind(ItemService.class).to(ItemServiceImpl.class).in(Singleton.class);
        bind(MarketResyncService.class).to(MarketResyncServiceImpl.class).in(Singleton.class);
        bind(MaterialService.class).to(MaterialServiceImpl.class).in(Singleton.class);
        bind(PreviewHandler.class).to(PreviewHandlerImpl.class).in(Singleton.class);
        bind(ProtectionService.class).to(ProtectionServiceImpl.class).in(Singleton.class);
        bind(Security.class).to(SecurityImpl.class).in(Singleton.class);
        bind(ShopBlockService.class).to(ShopBlockServiceImpl.class).in(Singleton.class);
        bind(ShopFinderService.class).to(ShopFinderServiceImpl.class).in(Singleton.class);
        bind(ShopService.class).to(ShopServiceImpl.class).in(Singleton.class);
        bind(TransactionService.class).to(TransactionServiceImpl.class).in(Singleton.class);

        // ChestShopSign is a static-heavy sign-format util with a small instance surface,
        // not an interface/impl service — bound concrete.
        bind(ChestShopSign.class).in(Singleton.class);
    }
}
