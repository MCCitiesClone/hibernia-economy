package io.paradaux.business.utils.resolvers;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import io.paradaux.business.services.FirmSuggestionCache;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves an {@link OnlineFirmName} argument: accepts any non-blank token (existence
 * is validated downstream), and tab-completes from firms that have an employee or
 * proprietor currently online — the union of each online player's firms. PAR-13.
 */
@Singleton
public final class OnlineFirmNameResolver implements ParameterResolver<OnlineFirmName> {

    private final FirmSuggestionCache cache;

    @Inject
    public OnlineFirmNameResolver(FirmSuggestionCache cache) {
        this.cache = cache;
    }

    @Override
    public Class<OnlineFirmName> type() {
        return OnlineFirmName.class;
    }

    @Override
    public Optional<OnlineFirmName> resolve(String token, CommandSender sender) {
        return (token == null || token.isBlank()) ? Optional.empty() : Optional.of(new OnlineFirmName(token.trim()));
    }

    @Override
    public List<String> suggestions(String prefix, CommandSender sender) {
        Set<String> pool = new LinkedHashSet<>();
        // Suggestions run on a Netty thread; snapshot the online players first so a
        // concurrent join/quit can't throw a ConcurrentModificationException mid-iteration.
        for (Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            pool.addAll(cache.playerFirmNames(online.getUniqueId()));
        }
        return FirmSuggestionCache.match(pool, prefix, 20);
    }
}
