package io.paradaux.jobs.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The tab-completion prefix filter. */
class SuggestionsTest {

    private static final List<String> POOL =
            List.of("trades/electrician", "electrician", "government/president", "president");

    @Test
    void matchesArePrefixBasedAndCaseInsensitive() {
        assertThat(Suggestions.match(POOL, "elec", 10)).containsExactly("electrician");
        assertThat(Suggestions.match(POOL, "ELEC", 10)).containsExactly("electrician");
        assertThat(Suggestions.match(POOL, "trades/", 10)).containsExactly("trades/electrician");
    }

    @Test
    void anEmptyOrNullPrefixMatchesEverything() {
        assertThat(Suggestions.match(POOL, "", 10)).hasSize(4);
        assertThat(Suggestions.match(POOL, null, 10)).hasSize(4);
    }

    @Test
    void resultsAreSortedDeduplicatedAndCapped() {
        assertThat(Suggestions.match(List.of("b", "a", "a", "c"), "", 10))
                .containsExactly("a", "b", "c");
        assertThat(Suggestions.match(POOL, "", 2)).hasSize(2);
    }

    @Test
    void aNonMatchingPrefixYieldsNothing() {
        assertThat(Suggestions.match(POOL, "zzz", 10)).isEmpty();
        assertThat(Suggestions.match(List.of(), "a", 10)).isEmpty();
    }
}
