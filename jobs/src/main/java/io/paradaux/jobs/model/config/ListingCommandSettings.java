package io.paradaux.jobs.model.config;

import java.util.List;

/**
 * One top-level listing command declared in {@code jobs.yml}.
 *
 * <p>The map key in {@code listing-commands:} is the command name, so
 * {@code qual:} creates {@code /qual}. This record carries the rest: which type it
 * lists, and any extra names it answers to.</p>
 *
 * @param type    the type key whose jobs this command shows
 * @param aliases additional command names, e.g. {@code quals}, {@code qualifications}
 */
public record ListingCommandSettings(String type, List<String> aliases) {

    public ListingCommandSettings {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
