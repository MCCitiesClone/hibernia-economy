package io.paradaux.jobs.commands.resolvers;

/**
 * A raw job token as typed: either {@code type/job} or a bare job key.
 *
 * <p>Deliberately unresolved. Turning it into a {@code JobId} needs the configured
 * catalogue, and doing that in the resolver would surrender the error message to the
 * framework's generic "invalid argument". Keeping the raw text lets the command say
 * "no such job: wizard" or "'inspector' matches more than one type".</p>
 */
public record JobArg(String value) {
}
