package io.paradaux.common.messaging;

import io.paradaux.hibernia.framework.i18n.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drop-in replacement for {@link Message#send(CommandSender, String, Object...)} for
 * templates that use a {@code {placeholder}} <em>inside a MiniMessage tag argument</em>,
 * such as a clickable command or link:
 *
 * <pre>{@code business.firm.list.line=<click:run_command:'/db info {firm}'>{firm}</click>}</pre>
 *
 * <h2>Why this is needed</h2>
 * <p>{@link Message} renders placeholders by rewriting each {@code {name}} into a
 * generated tag ({@code <ph0>}, {@code <ph1>}) and binding a MiniMessage tag resolver
 * for it. That works everywhere a tag is legal — but a tag argument is a
 * <em>literal string</em>, not markup, so MiniMessage never resolves a tag there. The
 * example above reaches the client as a button that runs the literal command
 * {@code /db info <ph1>}.</p>
 *
 * <p>Not every tag argument is literal: {@code <hover:show_text:'...'>} takes a
 * <em>component</em> argument and MiniMessage does resolve tags inside it, so hover
 * text already works and is deliberately left untouched. {@link #LITERAL_ARG_TAGS}
 * lists the tags whose arguments are literal strings and therefore need this
 * treatment. {@code TagArgumentAudit} in {@code :test-support} fails the build if a
 * bundle ever puts a placeholder inside a tag that is on neither list.</p>
 *
 * <h2>What this does</h2>
 * <p>It resolves the template for the recipient's locale, substitutes the caller's
 * values directly into literal tag arguments as escaped plain text, and hands the
 * rewritten template back to {@link Message} for the actual render. Everything else
 * is unchanged: the framework still does palette expansion, PlaceholderAPI
 * resolution, locale fallback, and the usual inert / {@link Message.Rich} /
 * {@link ComponentLike} treatment of values in the message <em>body</em>. When a
 * template has no placeholder in a literal tag argument, the call delegates straight
 * to {@link Message} and behaves identically.</p>
 *
 * <p>A value inlined into a tag argument is reduced to plain text — a click command
 * or URL is a string, so markup and styling have no meaning there. It is escaped for
 * the surrounding quote character, so a value containing an apostrophe cannot
 * terminate the argument early or inject a second tag.</p>
 */
public final class TagAwareMessage {

    /**
     * Tags whose arguments MiniMessage treats as literal strings, so a generated
     * {@code <phN>} tag inside one would not resolve. Component-valued arguments
     * ({@code hover:show_text}, {@code lang} / {@code translate}) are absent on
     * purpose: those already resolve and must keep their styling.
     */
    public static final Set<String> LITERAL_ARG_TAGS = Set.of("click", "insert");

    /** Mirrors the framework's placeholder syntax so both agree on what a name is. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.]+)}");

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private TagAwareMessage() {
    }

    /** As {@link Message#send(CommandSender, String, Object...)}, with tag arguments resolved. */
    public static void send(Message message, CommandSender to, String key, Object... kvPairs) {
        String rewritten = rewrittenPattern(message, to, key, kvToMap(kvPairs));
        if (rewritten == null) {
            message.send(to, key, kvPairs);   // nothing to fix: the framework path, unchanged
            return;
        }
        to.sendMessage(render(message, to, key, rewritten, kvToMap(kvPairs)));
    }

    /** As {@link #send}, but returns the component instead of sending it. */
    public static Component component(Message message, CommandSender to, String key, Object... kvPairs) {
        Map<String, Object> values = kvToMap(kvPairs);
        String rewritten = rewrittenPattern(message, to, key, values);
        return rewritten == null
                ? message.componentOr(to, key, null, values)
                : render(message, to, key, rewritten, values);
    }

    /**
     * The template for {@code key} with literal tag arguments filled in, or {@code null}
     * when this key needs no special handling — either it is undefined (the framework
     * owns missing-key output) or it has no placeholder in a literal tag argument.
     */
    private static String rewrittenPattern(Message message, CommandSender to, String key,
                                           Map<String, Object> values) {
        if (!message.has(to, key)) {
            return null;
        }
        String pattern = patternFor(message, to, key);
        String inlined = inlineTagArguments(pattern, name -> plainText(values.get(name)));
        return inlined.equals(pattern) ? null : inlined;
    }

    /**
     * Render the rewritten template through the framework. {@code componentOr} falls
     * back to the supplied pattern only when the key is undefined, so the key passed
     * here is a sentinel that no bundle can define — while keeping the real key's
     * namespace so {@code <namespace>.placeholder.*} entries still resolve.
     */
    private static Component render(Message message, CommandSender to, String key,
                                    String pattern, Map<String, Object> values) {
        return message.componentOr(to, sentinelKey(key), pattern, values);
    }

    /**
     * Substitute {@code {name}} placeholders that sit inside a literal tag argument
     * with their plain-text value, escaped for the quote that encloses them. Every
     * other placeholder is left for the framework to resolve.
     *
     * @param plainValue resolves a placeholder name to its plain text, or {@code null}
     *                   when the caller supplied no value for it.
     */
    public static String inlineTagArguments(String pattern, Function<String, String> plainValue) {
        if (pattern == null || pattern.indexOf('<') < 0 || pattern.indexOf('{') < 0) {
            return pattern;
        }
        StringBuilder out = new StringBuilder(pattern.length() + 32);
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c != '<') {
                out.append(c);
                i++;
                continue;
            }
            int close = tagEnd(pattern, i);
            if (close < 0) {          // a bare '<' in running text, not a tag
                out.append(c);
                i++;
                continue;
            }
            String tag = pattern.substring(i, close + 1);
            out.append(hasLiteralArgs(tag) ? substituteInQuotes(tag, plainValue) : tag);
            i = close + 1;
        }
        return out.toString();
    }

    /** Index of the {@code >} closing the tag opened at {@code start}, or -1 if there is none. */
    private static int tagEnd(String s, int start) {
        char quote = 0;
        for (int j = start + 1; j < s.length(); j++) {
            char c = s.charAt(j);
            if (quote != 0) {
                if (c == '\\') {
                    j++;              // an escaped character cannot close the quote
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '>') {
                return j;
            } else if (c == '<') {
                return -1;            // another '<' first — the outer one is literal text
            }
        }
        return -1;
    }

    /** Whether {@code tag} is an opening tag whose arguments MiniMessage treats as literal. */
    private static boolean hasLiteralArgs(String tag) {
        if (tag.length() < 2 || tag.charAt(1) == '/') {
            return false;
        }
        int end = 1;
        while (end < tag.length() && tag.charAt(end) != ':' && tag.charAt(end) != '>') {
            end++;
        }
        return LITERAL_ARG_TAGS.contains(tag.substring(1, end).toLowerCase(Locale.ROOT));
    }

    /** Replace placeholders inside each quoted argument of {@code tag}. */
    private static String substituteInQuotes(String tag, Function<String, String> plainValue) {
        StringBuilder out = new StringBuilder(tag.length() + 32);
        int i = 0;
        while (i < tag.length()) {
            char quote = tag.charAt(i);
            if (quote != '\'' && quote != '"') {
                out.append(quote);
                i++;
                continue;
            }
            StringBuilder arg = new StringBuilder();
            int j = i + 1;
            boolean closed = false;
            while (j < tag.length()) {
                char c = tag.charAt(j);
                if (c == '\\' && j + 1 < tag.length()) {
                    arg.append(c).append(tag.charAt(j + 1));
                    j += 2;
                } else if (c == quote) {
                    closed = true;
                    break;
                } else {
                    arg.append(c);
                    j++;
                }
            }
            if (!closed) {            // unbalanced quote — leave the remainder untouched
                return out.append(tag, i, tag.length()).toString();
            }
            out.append(quote).append(substitute(arg.toString(), quote, plainValue)).append(quote);
            i = j + 1;
        }
        return out.toString();
    }

    private static String substitute(String argument, char quote, Function<String, String> plainValue) {
        Matcher m = PLACEHOLDER.matcher(argument);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = plainValue.apply(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    value == null ? m.group() : escapeArgument(value, quote)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Escape a value for a quoted MiniMessage tag argument: a backslash or the
     * enclosing quote would otherwise end the argument early and let the rest of the
     * value be read as markup. Newlines cannot appear in a tag at all.
     */
    private static String escapeArgument(String value, char quote) {
        return value.replace("\\", "\\\\")
                .replace(String.valueOf(quote), "\\" + quote)
                .replace('\n', ' ')
                .replace('\r', ' ');
    }

    /** The plain text of a placeholder value, matching how it would read in a command or URL. */
    private static String plainText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Message.Rich rich) {
            return PLAIN.serialize(MM.deserialize(rich.value()));
        }
        if (value instanceof ComponentLike componentLike) {
            return PLAIN.serialize(componentLike.asComponent());
        }
        return String.valueOf(value);
    }

    /**
     * The template for {@code key} with the palette expanded and caller placeholders
     * still intact, resolved in the recipient's locale (the framework's configured
     * default for non-players, which is why the locale-less overload is used there).
     */
    private static String patternFor(Message message, CommandSender to, String key) {
        if (to instanceof Player player) {
            Locale locale = player.locale();
            if (locale != null) {
                return message.format(locale, key, Map.of());
            }
        }
        return message.format(key, Map.of());
    }

    /** A key no properties file can define, carrying {@code key}'s namespace. */
    private static String sentinelKey(String key) {
        int dot = key.indexOf('.');
        return (dot > 0 ? key.substring(0, dot) : "") + ". tag-argument";
    }

    private static Map<String, Object> kvToMap(Object... kvPairs) {
        if ((kvPairs.length & 1) == 1) {
            throw new IllegalArgumentException("Placeholder arguments must be in pairs: key, value, ...");
        }
        Map<String, Object> map = new LinkedHashMap<>(kvPairs.length / 2);
        for (int i = 0; i < kvPairs.length; i += 2) {
            if (!(kvPairs[i] instanceof String name)) {
                throw new IllegalArgumentException("Placeholder name at index " + i + " must be a String");
            }
            map.put(name, kvPairs[i + 1]);
        }
        return map;
    }
}
