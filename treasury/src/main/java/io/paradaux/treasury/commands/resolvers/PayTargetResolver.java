package io.paradaux.treasury.commands.resolvers;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import io.paradaux.treasury.model.economy.Account;
import io.paradaux.treasury.services.AccountService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Suggestion + binding resolver for {@code /pay <target>}. Suggests the two
 * things {@code /pay} can actually resolve: online player names and
 * (non-archived) government account display names.
 *
 * <p>{@link #resolve} never returns empty so the framework does not short-circuit
 * with its generic "Invalid target" — {@code PayCommand} distinguishes
 * player / government / unknown and owns the i18n rejection message.
 */
@Singleton
public class PayTargetResolver implements ParameterResolver<PayTarget> {

    private static final int MAX_SUGGESTIONS = 20;

    /**
     * Government accounts change rarely (created/archived via {@code /gov}), so
     * their names are cached to keep tab-completion — which runs inline on the
     * suggestion thread, per keystroke — off the database.
     */
    private static final long GOV_CACHE_TTL_MS = 60_000L;

    private final AccountService accountService;
    private volatile List<String> govNamesCache = List.of();
    private volatile long govNamesCachedAt = Long.MIN_VALUE;

    @Inject
    public PayTargetResolver(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public Class<PayTarget> type() {
        return PayTarget.class;
    }

    @Override
    public Optional<PayTarget> resolve(String token, CommandSender sender) {
        return Optional.of(new PayTarget(token));
    }

    @Override
    public List<String> suggestions(String prefix, CommandSender sender) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(p)) out.add(name);
        }
        for (String gov : governmentNames()) {
            if (gov.toLowerCase(Locale.ROOT).startsWith(p)) out.add(gov);
        }
        return out.size() > MAX_SUGGESTIONS ? out.subList(0, MAX_SUGGESTIONS) : out;
    }

    private List<String> governmentNames() {
        long now = System.currentTimeMillis();
        if (now - govNamesCachedAt > GOV_CACHE_TTL_MS) {
            List<String> names = new ArrayList<>();
            for (Account a : accountService.listGovernmentAccounts()) {
                if (!a.isArchived() && a.getDisplayName() != null) names.add(a.getDisplayName());
            }
            govNamesCache = List.copyOf(names);
            govNamesCachedAt = now;
        }
        return govNamesCache;
    }
}
