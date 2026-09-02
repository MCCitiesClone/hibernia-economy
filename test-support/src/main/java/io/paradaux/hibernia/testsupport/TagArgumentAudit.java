package io.paradaux.hibernia.testsupport;

import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards the one place the framework's placeholder substitution does not reach: the
 * argument of a MiniMessage tag.
 *
 * <p>{@code Message} renders a {@code {placeholder}} by rewriting it to a generated
 * tag ({@code <ph0>}) and binding a resolver. MiniMessage resolves tags in markup,
 * but a tag argument such as {@code <click:run_command:'...'>} is a <em>literal
 * string</em> — the generated tag is never expanded there and leaks to the client as
 * the text {@code <ph0>}. Component-valued arguments ({@code <hover:show_text:'…'>},
 * {@code <lang:…>}) are the exception: MiniMessage does parse those, so they work.</p>
 *
 * <p>This audit fails when a bundle puts a placeholder inside a tag argument that is
 * neither known-parsed nor handled by {@code TagAwareMessage} in {@code :common-paper}.
 * Templates in {@link #handledTags()} are fine <em>provided</em> the call site sends
 * them through {@code TagAwareMessage}; {@link #assertHandledKeysUseTagAwareMessage}
 * checks that half.</p>
 */
public final class TagArgumentAudit {

    /**
     * Tags whose arguments MiniMessage parses as markup — a placeholder inside one
     * resolves correctly with no special handling.
     */
    private static final Set<String> PARSED_ARG_TAGS = Set.of("hover", "lang", "tr", "translate", "trans");

    /**
     * Tags whose arguments are literal strings. {@code TagAwareMessage} inlines
     * placeholder values into these; keep in sync with its {@code LITERAL_ARG_TAGS}.
     */
    private static final Set<String> HANDLED_ARG_TAGS = Set.of("click", "insert");

    /** An opening tag with at least one argument, captured up to its closing {@code >}. */
    private static final Pattern TAG = Pattern.compile("<([a-zA-Z_][a-zA-Z0-9_]*):((?:'[^']*'|\"[^\"]*\"|[^<>])*)>");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.]+)}");

    private TagArgumentAudit() {
    }

    /** Tags this audit considers handled by {@code TagAwareMessage}. */
    public static Set<String> handledTags() {
        return HANDLED_ARG_TAGS;
    }

    /**
     * Fail if {@code messagesFile} puts a placeholder inside a tag argument that
     * neither MiniMessage resolves nor {@code TagAwareMessage} rewrites — i.e. a case
     * that would silently ship a broken button, link or command.
     *
     * @return the keys that legitimately need {@code TagAwareMessage}, for
     *         {@link #assertHandledKeysUseTagAwareMessage}.
     */
    public static List<String> assertNoUnhandledTagArguments(Path messagesFile) {
        List<String> needsHelper = new ArrayList<>();
        List<String> unhandled = new ArrayList<>();

        for (Entry entry : logicalLines(messagesFile)) {
            Matcher tag = TAG.matcher(entry.value());
            while (tag.find()) {
                String name = tag.group(1).toLowerCase(Locale.ROOT);
                if (!PLACEHOLDER.matcher(tag.group(2)).find() || PARSED_ARG_TAGS.contains(name)) {
                    continue;
                }
                if (HANDLED_ARG_TAGS.contains(name)) {
                    if (!needsHelper.contains(entry.key())) {
                        needsHelper.add(entry.key());
                    }
                } else {
                    unhandled.add(entry.key() + "  ->  <" + name + ":" + tag.group(2) + ">");
                }
            }
        }

        Assertions.assertTrue(unhandled.isEmpty(), () -> """
                %s: placeholder inside an unhandled MiniMessage tag argument.

                A tag argument is a literal string, so the framework's generated <phN> tag \
                is not resolved there and leaks to the client verbatim.

                Fix by either:
                  * adding the tag to TagAwareMessage.LITERAL_ARG_TAGS and this audit's \
                HANDLED_ARG_TAGS (if its argument is a literal string), or
                  * adding it to PARSED_ARG_TAGS (if MiniMessage parses that argument as \
                markup — verify before assuming), or
                  * moving the placeholder out of the tag argument.

                Offending entries:
                  %s""".formatted(messagesFile.getFileName(), String.join("\n  ", unhandled)));

        return needsHelper;
    }

    /**
     * Fail if any key returned by {@link #assertNoUnhandledTagArguments} is still sent
     * through plain {@code message.send(...)} rather than {@code TagAwareMessage}.
     *
     * @param sourceRoot the plugin's main Java source root.
     * @param keys       the keys whose templates need the helper.
     */
    public static void assertHandledKeysUseTagAwareMessage(Path sourceRoot, List<String> keys) {
        String sources = readAll(sourceRoot);
        List<String> bad = new ArrayList<>();

        for (String key : keys) {
            String literal = '"' + key + '"';
            int at = sources.indexOf(literal);
            if (at < 0) {
                continue;   // unused key — MessageKeyAudit owns that complaint
            }
            while (at >= 0) {
                if (!isTagAwareCall(sources, at)) {
                    bad.add(key);
                    break;
                }
                at = sources.indexOf(literal, at + literal.length());
            }
        }

        Assertions.assertTrue(bad.isEmpty(), () -> """
                These keys put a placeholder inside a literal MiniMessage tag argument \
                (a clickable command, link or copy target), so they must be sent with \
                TagAwareMessage.send(message, sender, key, ...) from :common-paper. \
                Plain Message.send leaves the argument as a literal <phN> tag and ships \
                a dead button.

                Offending keys:
                  %s""".formatted(String.join("\n  ", bad)));
    }

    /** Whether the call surrounding the key literal at {@code at} is a TagAwareMessage one. */
    private static boolean isTagAwareCall(String sources, int at) {
        int from = Math.max(0, at - 400);
        String before = sources.substring(from, at);
        int call = before.lastIndexOf("TagAwareMessage.");
        if (call < 0) {
            return false;
        }
        // No intervening ';' or '}' — otherwise the match belongs to an earlier statement.
        String between = before.substring(call);
        return between.indexOf(';') < 0 && between.indexOf('}') < 0;
    }

    private static String readAll(Path sourceRoot) {
        StringBuilder sb = new StringBuilder();
        try (var paths = Files.walk(sourceRoot)) {
            for (Path p : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                sb.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }

    /** One properties entry, with backslash line-continuations joined. */
    private record Entry(String key, String value) {
    }

    private static List<Entry> logicalLines(Path messagesFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(messagesFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.stripLeading();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int eq = indexOfSeparator(line);
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            StringBuilder value = new StringBuilder(line.substring(eq + 1));
            while (value.length() > 0 && value.charAt(value.length() - 1) == '\\' && i + 1 < lines.size()) {
                value.setLength(value.length() - 1);
                value.append(lines.get(++i));
            }
            if (seen.add(key + "#" + i)) {
                entries.add(new Entry(key, value.toString()));
            }
        }
        return entries;
    }

    private static int indexOfSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '=' || c == ':') {
                return i;
            } else if (Character.isWhitespace(c)) {
                return -1;   // a continuation line, not a new entry
            }
        }
        return -1;
    }
}
