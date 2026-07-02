package io.paradaux.chestshop.services;

import io.paradaux.chestshop.dialogs.FindState;
import io.paradaux.chestshop.model.FoundShop;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.treasury.api.ShopQueryApi;
import io.paradaux.treasury.api.market.ShopResult;
import io.paradaux.treasury.api.market.ShopSearchQuery;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Runs a {@code /find} search off the main thread and returns the sorted,
 * filtered, distance-scored results. The registry read (item exact/LIKE, world,
 * active+visible) happens in Treasury via {@link ShopQueryApi}; the shop-type /
 * hide-empty-full filtering, distance and weighted sort happen here through
 * {@link FindState#pipeline}.
 */
@Singleton
public class ShopFinderService {

    private final JavaPlugin plugin;

    @Inject
    public ShopFinderService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** True when the registry read API is wired (Treasury present). */
    public boolean available() {
        return MarketHook.searchEnabled();
    }

    /** Search asynchronously; the future completes with the page-ready, pipelined results. */
    public CompletableFuture<List<FoundShop>> find(FindState state) {
        CompletableFuture<List<FoundShop>> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(query(state));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /** The blocking query + in-memory pipeline. Package-private for testing with a stubbed FindState. */
    List<FoundShop> query(FindState state) {
        ShopQueryApi api = MarketHook.shopQuery();
        if (api == null) {
            return List.of();
        }
        List<ShopResult> rows = api.searchShops(ShopSearchQuery.builder()
                .itemKey(state.itemKey())
                .fuzzy(state.fuzzy())
                .world(state.queryWorld())   // /find is scoped to the player's world (legacy behaviour)
                .build());
        List<FoundShop> found = rows.stream()
                .map(r -> FoundShop.from(r, state.queryWorld(), state.qx(), state.qy(), state.qz(),
                        state.hasPosition()))
                .toList();
        return state.pipeline(found);
    }
}
