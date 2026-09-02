package io.paradaux.common.messaging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the template rewrite behind {@link TagAwareMessage}. The framework's own
 * placeholder-to-tag rewrite is simulated here (a {@code {name}} the rewrite leaves
 * alone becomes {@code <phN>}), so each case can assert on the click payload
 * MiniMessage actually produces — which is the thing that was broken.
 */
class TagAwareMessageTest {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static Function<String, String> values(Map<String, String> map) {
        return map::get;
    }

    /** What the framework does after the rewrite: every surviving {name} becomes a tag. */
    private static Component render(String rewritten, Map<String, String> values) {
        String tagged = rewritten;
        TagResolver.Builder resolvers = TagResolver.builder();
        int n = 0;
        for (Map.Entry<String, String> e : values.entrySet()) {
            String tag = "ph" + n++;
            tagged = tagged.replace("{" + e.getKey() + "}", "<" + tag + ">");
            resolvers.resolver(Placeholder.unparsed(tag, e.getValue()));
        }
        return MM.deserialize(tagged, resolvers.build());
    }

    private static ClickEvent firstClick(Component c) {
        if (c.style().clickEvent() != null) {
            return c.style().clickEvent();
        }
        for (Component child : c.children()) {
            ClickEvent found = firstClick(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void inlinesPlaceholderIntoRunCommandArgument() {
        Map<String, String> v = Map.of("firm", "Acme Co");
        String out = TagAwareMessage.inlineTagArguments(
                "<click:run_command:'/db info {firm}'>{firm}</click>", values(v));

        assertThat(out).isEqualTo("<click:run_command:'/db info Acme Co'>{firm}</click>");

        // The body placeholder still renders through the framework; the click payload
        // is now the real command instead of a leaked <phN> tag.
        Component rendered = render(out, v);
        assertThat(firstClick(rendered).value()).isEqualTo("/db info Acme Co");
        assertThat(MM.serialize(rendered)).contains("Acme Co");
    }

    @Test
    void inlinesEveryPlaceholderInAMultiArgumentCommand() {
        String out = TagAwareMessage.inlineTagArguments(
                "<click:run_command:'/business transfer confirm {firm} {target} {code}'>Confirm</click>",
                values(Map.of("firm", "Acme", "target", "evan", "code", "A1B2")));

        assertThat(firstClick(render(out, Map.of()))).isNotNull()
                .extracting(ClickEvent::value)
                .isEqualTo("/business transfer confirm Acme evan A1B2");
    }

    @Test
    void escapesQuotesSoAValueCannotTerminateTheArgument() {
        String out = TagAwareMessage.inlineTagArguments(
                "<click:run_command:'/db info {firm}'>x</click>",
                values(Map.of("firm", "O'Brien & Sons")));

        // The apostrophe is escaped in the template and survives as a literal in the
        // payload — it neither closes the argument nor splits the command.
        assertThat(out).contains("O\\'Brien");
        assertThat(firstClick(render(out, Map.of())).value()).isEqualTo("/db info O'Brien & Sons");
    }

    @Test
    void aValueCannotInjectMarkupOrASecondTag() {
        String out = TagAwareMessage.inlineTagArguments(
                "<click:run_command:'/db info {firm}'>x</click>",
                values(Map.of("firm", "x'><red>pwned</red><click:run_command:'/op evan")));

        Component rendered = render(out, Map.of());
        // The hostile value stays entirely inside one click payload: the action is
        // unchanged, no second click event was created, and no styling was injected.
        // (Serialising the component would echo the payload text, so assert on the
        // component tree instead.)
        assertThat(firstClick(rendered).action()).isEqualTo(ClickEvent.Action.RUN_COMMAND);
        assertThat(firstClick(rendered).value()).isEqualTo("/db info x'><red>pwned</red><click:run_command:'/op evan");
        assertThat(clickEventCount(rendered)).isEqualTo(1);
        assertThat(anyColoured(rendered)).isFalse();
    }

    private static int clickEventCount(Component c) {
        int n = c.style().clickEvent() != null ? 1 : 0;
        for (Component child : c.children()) {
            n += clickEventCount(child);
        }
        return n;
    }

    private static boolean anyColoured(Component c) {
        if (c.style().color() != null) {
            return true;
        }
        for (Component child : c.children()) {
            if (anyColoured(child)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void leavesHoverTextAloneBecauseMiniMessageResolvesTagsThere() {
        String pattern = "<hover:show_text:'seller {seller}'>x</hover>";
        assertThat(TagAwareMessage.inlineTagArguments(pattern, values(Map.of("seller", "evan"))))
                .isEqualTo(pattern);
    }

    @Test
    void leavesPlaceholdersOutsideTagArgumentsToTheFramework() {
        String pattern = "<green>{amount}</green> paid to {target}";
        assertThat(TagAwareMessage.inlineTagArguments(pattern, values(Map.of("amount", "$1.00"))))
                .isEqualTo(pattern);
    }

    @Test
    void leavesAPlaceholderWithNoSuppliedValueIntact() {
        assertThat(TagAwareMessage.inlineTagArguments(
                "<click:run_command:'/pay {who}'>x</click>", values(Map.of())))
                .isEqualTo("<click:run_command:'/pay {who}'>x</click>");
    }

    @Test
    void handlesOpenUrlAndCopyToClipboard() {
        assertThat(TagAwareMessage.inlineTagArguments(
                "<click:open_url:'{url}'>link</click>", values(Map.of("url", "https://x.io/a?b=1"))))
                .isEqualTo("<click:open_url:'https://x.io/a?b=1'>link</click>");

        assertThat(TagAwareMessage.inlineTagArguments(
                "<click:copy_to_clipboard:'{token}'>copy</click>", values(Map.of("token", "tok_ab.cd"))))
                .isEqualTo("<click:copy_to_clipboard:'tok_ab.cd'>copy</click>");
    }

    @Test
    void ignoresLiteralAngleBracketsInRunningText() {
        String pattern = "5 < 6 and {n} > 4";
        assertThat(TagAwareMessage.inlineTagArguments(pattern, values(Map.of("n", "5"))))
                .isEqualTo(pattern);
    }

    @Test
    void isANoOpForTemplatesWithoutTagsOrPlaceholders() {
        assertThat(TagAwareMessage.inlineTagArguments("plain text", values(Map.of()))).isEqualTo("plain text");
        assertThat(TagAwareMessage.inlineTagArguments(null, values(Map.of()))).isNull();
    }
}
