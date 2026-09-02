package io.paradaux.jobs.commands.resolvers;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.utils.Suggestions;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

/**
 * Accepts any non-blank token and suggests configured jobs.
 *
 * <p>Permissive on resolve, like Business's firm-name resolver: validation belongs to
 * the service, which can distinguish "no such job" from "ambiguous bare key" and name
 * the offending token.</p>
 */
@Singleton
public final class JobArgResolver implements ParameterResolver<JobArg> {

    /** Brigadier shows a short list; more than this is noise in the client. */
    private static final int SUGGESTION_LIMIT = 25;

    private final JobRegistry registry;

    @Inject
    public JobArgResolver(JobRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Class<JobArg> type() {
        return JobArg.class;
    }

    @Override
    public Optional<JobArg> resolve(String token, CommandSender sender) {
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(new JobArg(token));
    }

    /**
     * Reads only the precomputed snapshot list.
     *
     * <p>This always runs off the main thread, so it must not touch Bukkit state,
     * LuckPerms or the database — the snapshot is immutable and published atomically,
     * which makes it the one safe thing to read here.</p>
     */
    @Override
    public List<String> suggestions(String prefix, CommandSender sender) {
        return Suggestions.match(registry.snapshot().suggestions(), prefix, SUGGESTION_LIMIT);
    }
}
