package io.paradaux.jobs.utils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Pure, stateless tab-completion prefix filter. Mirrors Business's helper of the same name. */
public final class Suggestions {

    private Suggestions() {
    }

    /** Case-insensitive prefix match: distinct, sorted, capped at {@code limit}. */
    public static List<String> match(Collection<String> pool, String prefix, int limit) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return pool.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(needle))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(limit)
                .collect(Collectors.toList());
    }
}
