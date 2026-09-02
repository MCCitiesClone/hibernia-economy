package io.paradaux.jobs.commands.resolvers;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import io.paradaux.jobs.api.model.JobType;
import io.paradaux.jobs.services.JobRegistry;
import io.paradaux.jobs.utils.Suggestions;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

/** Accepts any non-blank token and suggests configured type keys. */
@Singleton
public final class JobTypeArgResolver implements ParameterResolver<JobTypeArg> {

    private static final int SUGGESTION_LIMIT = 25;

    private final JobRegistry registry;

    @Inject
    public JobTypeArgResolver(JobRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Class<JobTypeArg> type() {
        return JobTypeArg.class;
    }

    @Override
    public Optional<JobTypeArg> resolve(String token, CommandSender sender) {
        return token == null || token.isBlank()
                ? Optional.empty() : Optional.of(new JobTypeArg(token));
    }

    @Override
    public List<String> suggestions(String prefix, CommandSender sender) {
        List<String> keys = registry.snapshot().types().stream().map(JobType::key).toList();
        return Suggestions.match(keys, prefix, SUGGESTION_LIMIT);
    }
}
