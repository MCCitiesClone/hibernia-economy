package io.paradaux.chestshop.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.paradaux.chestshop.dao.ItemCodeRepository;
import io.paradaux.chestshop.dao.impl.SqliteItemCodeRepository;
import io.paradaux.chestshop.services.ItemCodeService;

/**
 * ChestShop's own Guice bindings — its service and DAO layer, alongside the
 * framework's {@code HiberniaModule}. This is the seam the plugin is migrating
 * onto: thin entrypoints → {@code services/} (business logic) → {@code dao/}
 * (persistence), replacing the static God-classes and the internal event-bus
 * pipelines. The DAO interfaces are storage-agnostic; the impls bound here are
 * SQLite today and can be swapped to the shared MariaDB without touching callers.
 */
public class ChestShopModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ItemCodeRepository.class).to(SqliteItemCodeRepository.class).in(Singleton.class);
        bind(ItemCodeService.class).in(Singleton.class);
    }
}
