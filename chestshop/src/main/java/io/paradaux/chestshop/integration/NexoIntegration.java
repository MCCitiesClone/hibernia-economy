package io.paradaux.chestshop.integration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.services.ItemService;
import org.bukkit.plugin.Plugin;

/**
 * Nexo soft-dependency: enables native Nexo/ItemsAdder custom-item resolution in
 * {@link ItemService}, keeping the {@code com.nexomc.nexo} classes off the path unless Nexo
 * is present.
 */
@Singleton
public class NexoIntegration implements Integration {

    private final ItemService items;

    @Inject
    public NexoIntegration(ItemService items) {
        this.items = items;
    }

    @Override
    public String pluginName() {
        return "Nexo";
    }

    @Override
    public boolean hook(Plugin plugin) {
        items.enableNexo();
        return true;
    }
}
