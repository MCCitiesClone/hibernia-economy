package io.paradaux.jobs.utils;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Normalises the {@code color:} values operators write in {@code jobs.yml} into
 * MiniMessage colour tags.
 *
 * <p>Operators write colours several equally reasonable ways — {@code aqua},
 * {@code <aqua>}, {@code #f0b040}, {@code <#f0b040>} — and being fussy about which
 * one is right would be a pointless papercut. All four normalise to the same tag.
 * Anything that is not a recognised named colour or a six-digit hex is rejected, so
 * a typo surfaces as a startup warning rather than as raw text leaking into a
 * section header.</p>
 */
public final class JobColors {

    /** The named colours MiniMessage understands. */
    private static final Set<String> NAMED = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white");

    private JobColors() {
    }

    /**
     * Normalise a configured colour to a MiniMessage tag such as {@code <aqua>} or
     * {@code <#f0b040>}.
     *
     * @return the tag, or empty when the value is absent or not a valid colour.
     */
    public static Optional<String> normalise(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        // Tolerate the value already being written as a tag.
        if (value.startsWith("<") && value.endsWith(">")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (NAMED.contains(value)) {
            return Optional.of("<" + value + ">");
        }
        if (isHex(value)) {
            return Optional.of("<" + (value.startsWith("#") ? value : "#" + value) + ">");
        }
        return Optional.empty();
    }

    /** The closing tag matching {@link #normalise}'s output, e.g. {@code </aqua>}. */
    public static String closing(String openingTag) {
        if (openingTag == null || openingTag.length() < 3
                || !openingTag.startsWith("<") || !openingTag.endsWith(">")) {
            return "";
        }
        return "</" + openingTag.substring(1);
    }

    private static boolean isHex(String value) {
        String digits = value.startsWith("#") ? value.substring(1) : value;
        if (digits.length() != 6) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (Character.digit(digits.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
