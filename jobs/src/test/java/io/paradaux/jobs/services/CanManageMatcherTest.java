package io.paradaux.jobs.services;

import io.paradaux.jobs.api.model.JobId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The selector grammar, exhaustively — this is what decides who can fire whom. */
class CanManageMatcherTest {

    private static final JobId TARGET = JobId.of("government", "president");

    @ParameterizedTest(name = "[{index}] \"{0}\" matches government/president = {1}")
    @CsvSource({
            // exact
            "government/president,      true",
            "government/senator,        false",
            "legal/president,           false",
            // job wildcard: every job of a type
            "government/*,              true",
            "legal/*,                   false",
            // type wildcard: that job key in any type
            "*/president,               true",
            "*/senator,                 false",
            // full wildcards
            "*/*,                       true",
            "*,                         true",
            // case and whitespace insensitivity
            "GOVERNMENT/PRESIDENT,      true",
            "  government/president  ,  true",
            // malformed selectors never match
            "government,                false",
            "government/,               false",
            "/president,                false",
            "government/pres/ident,     false",
            "'',                        false",
    })
    void matchesFollowsTheGrammar(String selector, boolean expected) {
        assertThat(CanManageMatcher.matches(selector, TARGET)).isEqualTo(expected);
    }

    @Test
    void nullsNeverMatch() {
        assertThat(CanManageMatcher.matches(null, TARGET)).isFalse();
        assertThat(CanManageMatcher.matches("*", null)).isFalse();
    }

    @Test
    void canManageIsTrueWhenAnySelectorMatches() {
        Set<String> selectors = Set.of("legal/*", "government/president");
        assertThat(CanManageMatcher.canManage(selectors, TARGET)).isTrue();
        assertThat(CanManageMatcher.canManage(selectors, JobId.of("trades", "electrician"))).isFalse();
    }

    @Test
    void canManageHandlesEmptyAndNullSelectorSets() {
        assertThat(CanManageMatcher.canManage(Set.of(), TARGET)).isFalse();
        assertThat(CanManageMatcher.canManage(null, TARGET)).isFalse();
    }

    @Test
    void authorityIsNotTransitive() {
        // A minister may manage the clerk. The clerk's own selectors are all that
        // matter for what the CLERK may do — holding a managed job confers nothing.
        Set<String> ministerSelectors = Set.of("government/clerk");
        Set<String> clerkSelectors = Set.of();
        JobId clerk = JobId.of("government", "clerk");

        assertThat(CanManageMatcher.canManage(ministerSelectors, clerk)).isTrue();
        assertThat(CanManageMatcher.canManage(clerkSelectors, clerk)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "a/b", "*/b", "a/*", "*/*"})
    void validSelectorsAreAccepted(String selector) {
        assertThat(CanManageMatcher.isValidSelector(selector)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "a", "/b", "a/", "a/b/c"})
    void invalidSelectorsAreRejected(String selector) {
        assertThat(CanManageMatcher.isValidSelector(selector)).isFalse();
    }

    @Test
    void isConcreteDistinguishesWildcardsFromNamedJobs() {
        // Used to warn about selectors naming a job that does not exist; a wildcard
        // is never "dangling", so only concrete selectors are checked.
        assertThat(CanManageMatcher.isConcrete("government/president")).isTrue();
        assertThat(CanManageMatcher.isConcrete("government/*")).isFalse();
        assertThat(CanManageMatcher.isConcrete("*/president")).isFalse();
        assertThat(CanManageMatcher.isConcrete("*")).isFalse();
        assertThat(CanManageMatcher.isConcrete("nonsense")).isFalse();
    }
}
