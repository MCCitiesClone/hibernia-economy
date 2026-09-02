package io.paradaux.jobs.model;

import java.util.List;

/**
 * A resolved listing command: a top-level root that shows one type's jobs.
 *
 * <p>Registered at runtime from {@code jobs.yml} rather than declared with
 * {@code @Command}, so an operator can add {@code /qual} — or rename it, or point it
 * at a different type — without a code change. {@code jobs.yml} is the source of
 * truth for which of these exist.</p>
 *
 * @param name    the primary command name, without a slash
 * @param aliases additional names the command answers to
 * @param typeKey the configured type whose jobs it lists
 */
public record ListingCommand(String name, List<String> aliases, String typeKey) {

    public ListingCommand {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    /** Every name this command occupies, primary first. */
    public List<String> allNames() {
        List<String> names = new java.util.ArrayList<>(aliases.size() + 1);
        names.add(name);
        names.addAll(aliases);
        return List.copyOf(names);
    }
}
