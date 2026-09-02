package io.paradaux.jobs.services;

import io.paradaux.jobs.api.model.JobId;

import java.util.Locale;
import java.util.Set;

/**
 * Matches {@code can-manage} selectors against a job.
 *
 * <p>The grammar is deliberately tiny: {@code <type>/<job>}, where either half may
 * be a literal key or a {@code *} wildcard. A selector that is just {@code *} is
 * shorthand for "every type and every job".
 * There are no regexes, no prefix globs and no rank arithmetic — an operator reading
 * the config can tell exactly who can fire whom, and an auditor can grep for it.</p>
 *
 * <p>Matching is <strong>non-transitive</strong>. Holding a job that may manage a
 * manager does not confer the manager's own authority; the only selectors that count
 * are those on the jobs the actor actually holds. That is what "explicit lists"
 * means, and it keeps authority answerable by inspection rather than by traversal.</p>
 */
public final class CanManageMatcher {

    private static final String WILDCARD = "*";

    private CanManageMatcher() {
    }

    /**
     * Whether a single selector matches {@code target}.
     *
     * <p>A malformed selector never matches. It is reported once at snapshot-build
     * time rather than throwing here, so one bad line cannot break every check.</p>
     */
    public static boolean matches(String selector, JobId target) {
        if (selector == null || target == null) {
            return false;
        }
        String trimmed = selector.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return false;
        }
        if (WILDCARD.equals(trimmed)) {
            return true;
        }

        int slash = trimmed.indexOf('/');
        if (slash <= 0 || slash != trimmed.lastIndexOf('/') || slash == trimmed.length() - 1) {
            return false;   // not exactly two non-empty segments
        }
        String typePart = trimmed.substring(0, slash);
        String jobPart = trimmed.substring(slash + 1);

        return segmentMatches(typePart, target.type()) && segmentMatches(jobPart, target.job());
    }

    /** Whether any of {@code selectors} matches {@code target}. */
    public static boolean canManage(Set<String> selectors, JobId target) {
        if (selectors == null || selectors.isEmpty() || target == null) {
            return false;
        }
        for (String selector : selectors) {
            if (matches(selector, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a selector is syntactically valid. Used by the registry to report
     * config errors at load time instead of leaving them to fail silently.
     */
    public static boolean isValidSelector(String selector) {
        if (selector == null) {
            return false;
        }
        String trimmed = selector.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (WILDCARD.equals(trimmed)) {
            return true;
        }
        int slash = trimmed.indexOf('/');
        return slash > 0 && slash == trimmed.lastIndexOf('/') && slash != trimmed.length() - 1;
    }

    /** Whether a valid selector names a concrete job rather than using a wildcard. */
    public static boolean isConcrete(String selector) {
        if (!isValidSelector(selector) || WILDCARD.equals(selector.trim())) {
            return false;
        }
        String trimmed = selector.trim();
        int slash = trimmed.indexOf('/');
        return !WILDCARD.equals(trimmed.substring(0, slash))
                && !WILDCARD.equals(trimmed.substring(slash + 1));
    }

    private static boolean segmentMatches(String segment, String actual) {
        return WILDCARD.equals(segment) || segment.equals(actual);
    }
}
