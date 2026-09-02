package io.paradaux.jobs.startup;

import io.paradaux.hibernia.testsupport.TagArgumentAudit;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

/**
 * Guards the one substitution the framework cannot perform: a {@code {placeholder}}
 * inside a MiniMessage tag argument.
 *
 * <p>A tag argument is a literal string, so the generated {@code <phN>} tag is never
 * resolved there and would ship a clickable entry whose command is the literal text
 * {@code <phN>}. Keys that need it must go through {@code TagAwareMessage}.</p>
 */
class JobsTagArgumentAuditTest {

    @Test
    void tagArgumentsAreHandled() {
        Path moduleRoot = Path.of(System.getProperty("user.dir"));
        List<String> needsHelper = TagArgumentAudit.assertNoUnhandledTagArguments(
                moduleRoot.resolve("src/main/resources/messages.properties"));
        TagArgumentAudit.assertHandledKeysUseTagAwareMessage(
                moduleRoot.resolve("src/main/java"), needsHelper);
    }
}
