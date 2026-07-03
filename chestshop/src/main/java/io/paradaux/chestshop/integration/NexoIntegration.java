package io.paradaux.chestshop.integration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.chestshop.services.CustomItemResolver;
import io.paradaux.chestshop.services.ItemCodeService;
import io.paradaux.chestshop.services.ItemService;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Nexo (and ItemsAdder) soft-dependency: registers itself as a {@link CustomItemResolver} on
 * {@link ItemService} so shop signs can parse/name Nexo custom items — the service consults the
 * resolver and never references Nexo directly (PAR-314).
 *
 * <p>The Nexo-API code lives in the {@link Nexo} helper — this integration's only user — so the
 * {@code com.nexomc.nexo} classes stay off the call path until Nexo is confirmed present (this
 * integration is instantiated at enable to build the {@code Set<Integration>} even when Nexo is
 * absent; its resolver methods, which touch {@code Nexo}, run only after {@link #hook} registers
 * it, i.e. only when Nexo is present).
 */
@Singleton
public class NexoIntegration implements Integration, CustomItemResolver {

    private final ItemService items;
    private final ItemCodeService itemCodes;

    @Inject
    public NexoIntegration(ItemService items, ItemCodeService itemCodes) {
        this.items = items;
        this.itemCodes = itemCodes;
    }

    @Override
    public String pluginName() {
        return "Nexo";
    }

    @Override
    public boolean hook(Plugin plugin) {
        Nexo.init(itemCodes);
        items.registerCustomItemResolver(this);
        return true;
    }

    @Override
    public ItemStack parseItem(String raw) {
        return Nexo.parseItem(raw);
    }

    @Override
    public String queryString(ItemStack stack, int maxWidth) {
        return Nexo.queryString(stack, maxWidth);
    }

    @Override
    public void reload() {
        Nexo.reload();
    }
}
