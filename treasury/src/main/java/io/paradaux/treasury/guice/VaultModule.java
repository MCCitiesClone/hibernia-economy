package io.paradaux.treasury.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import io.paradaux.treasury.adapters.VaultEconomyAdapter;

/**
 * Optional Guice module that binds the Vault economy adapter.
 * Only loaded if Vault is available on the classpath.
 */
public class VaultModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(VaultEconomyAdapter.class).in(Singleton.class);
    }
}

