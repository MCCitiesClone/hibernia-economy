package io.paradaux.jobs.startup;

import io.paradaux.hibernia.testsupport.MessageKeyAudit;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

/**
 * Bidirectional messages.properties ↔ source audit: every {@code jobs.*} key sent
 * through the i18n API must be defined, and every defined key must be used — no
 * missing-key placeholders reaching players, and no dead i18n accumulating.
 */
class JobsMessageKeyAuditTest {

    @Test
    void messagesAndSourceAgree() {
        Path moduleRoot = Path.of(System.getProperty("user.dir"));
        MessageKeyAudit.assertBidirectional(
                moduleRoot.resolve("src/main/java"),
                moduleRoot.resolve("src/main/resources/messages.properties"),
                List.of("jobs."),
                // placeholder.* are MiniMessage tokens expanded into other messages,
                // never sent as keys themselves.
                List.of("placeholder."));
    }
}
