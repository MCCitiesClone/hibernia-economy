package io.paradaux.treasury.commands.resolvers;

/**
 * Marker argument type for {@code /pay}'s {@code <target>}. A distinct type
 * (rather than a plain {@code String}) lets the command framework pick
 * {@link PayTargetResolver} for tab-completion instead of the no-op
 * {@code StringResolver}. Wraps the raw token verbatim — validity (player vs
 * government account vs unknown) is decided by the command, not the resolver.
 */
public record PayTarget(String name) {}
