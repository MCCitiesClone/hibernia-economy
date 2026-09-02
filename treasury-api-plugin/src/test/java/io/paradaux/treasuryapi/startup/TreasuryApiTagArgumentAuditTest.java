package io.paradaux.treasuryapi.startup;

import io.paradaux.hibernia.testsupport.TagArgumentAudit;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

/**
 * Guards TreasuryAPI's messages.properties against the one substitution the framework cannot
 * do: a {placeholder} inside a MiniMessage tag argument. A tag argument is a literal
 * string, so the generated <phN> tag is never resolved there and would ship a button,
 * link or copy target containing the literal text "<phN>".
 *
 * <p>Templates that legitimately need it must be sent via TagAwareMessage
 * (:common-paper), which inlines the value into the argument before rendering.</p>
 */
class TreasuryApiTagArgumentAuditTest {

    @Test
    void tagArgumentsAreHandled() {
        Path moduleRoot = Path.of(System.getProperty("user.dir"));
        List<String> needsHelper = TagArgumentAudit.assertNoUnhandledTagArguments(
                moduleRoot.resolve("src/main/resources/messages.properties"));
        TagArgumentAudit.assertHandledKeysUseTagAwareMessage(
                moduleRoot.resolve("src/main/java"), needsHelper);
    }
}
