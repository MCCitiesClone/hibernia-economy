package io.paradaux.jobs.services;

import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.model.config.JobsYaml;
import io.paradaux.jobs.utils.JobColors;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/** Colour normalisation, and the type → job inheritance rule. */
class JobColorTest {

    private Logger log;
    private List<LogRecord> logged;

    @BeforeEach
    void setUp() {
        logged = new ArrayList<>();
        log = Logger.getLogger("JobColorTest-" + System.nanoTime());
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logged.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
    }

    private JobSnapshot snapshotOf(String yaml) {
        return JobSnapshot.build(
                JobsYaml.parse(YamlConfiguration.loadConfiguration(new StringReader(yaml))), log);
    }

    // ---- normalisation ----

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource({
            // Operators write colours several reasonable ways; all mean the same.
            "aqua,       <aqua>",
            "<aqua>,     <aqua>",
            "AQUA,       <aqua>",
            "  gold  ,   <gold>",
            "dark_purple,<dark_purple>",
            "#f0b040,    <#f0b040>",
            "<#F0B040>,  <#f0b040>",
            "f0b040,     <#f0b040>",
    })
    void coloursNormaliseToAMiniMessageTag(String raw, String expected) {
        assertThat(JobColors.normalise(raw)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "<>", "notacolour", "#12345", "#gggggg", "#1234567"})
    void nonColoursAreRejected(String raw) {
        assertThat(JobColors.normalise(raw)).isEmpty();
    }

    @Test
    void nullIsRejectedRatherThanThrowing() {
        assertThat(JobColors.normalise(null)).isEmpty();
    }

    @Test
    void theClosingTagMatchesTheOpeningOne() {
        assertThat(JobColors.closing("<aqua>")).isEqualTo("</aqua>");
        assertThat(JobColors.closing("<#f0b040>")).isEqualTo("</#f0b040>");
        assertThat(JobColors.closing("")).isEmpty();
        assertThat(JobColors.closing(null)).isEmpty();
    }

    // ---- inheritance ----

    @Test
    void aTypeColourAppliesToItsSectionAndToEveryJobInIt() {
        JobSnapshot snapshot = snapshotOf("""
                types:
                  government:
                    color: gold
                    jobs:
                      president: {}
                      clerk: {}
                """);

        assertThat(snapshot.type("government").orElseThrow().color()).isEqualTo("<gold>");
        assertThat(snapshot.job(JobId.of("government", "president")).orElseThrow().color())
                .isEqualTo("<gold>");
        assertThat(snapshot.job(JobId.of("government", "clerk")).orElseThrow().color())
                .isEqualTo("<gold>");
    }

    @Test
    void aJobColourOverridesItsTypeWithoutAffectingSiblings() {
        JobSnapshot snapshot = snapshotOf("""
                types:
                  government:
                    color: gold
                    jobs:
                      president:
                        color: "#ffd700"
                      clerk: {}
                """);

        assertThat(snapshot.job(JobId.of("government", "president")).orElseThrow().color())
                .isEqualTo("<#ffd700>");
        assertThat(snapshot.job(JobId.of("government", "clerk")).orElseThrow().color())
                .isEqualTo("<gold>");
        // Overriding a job must not repaint the section header.
        assertThat(snapshot.type("government").orElseThrow().color()).isEqualTo("<gold>");
    }

    @Test
    void anUncolouredTypeAndItsJobsCarryNoColour() {
        JobSnapshot snapshot = snapshotOf("""
                types:
                  government:
                    jobs:
                      president: {}
                """);

        // Empty rather than a guessed default — the renderer falls back to the
        // message bundle's palette, which is the operator's real default.
        assertThat(snapshot.type("government").orElseThrow().hasColor()).isFalse();
        assertThat(snapshot.job(JobId.of("government", "president")).orElseThrow().hasColor())
                .isFalse();
    }

    @Test
    void aJobMayCarryAColourWhenItsTypeDoesNot() {
        JobSnapshot snapshot = snapshotOf("""
                types:
                  government:
                    jobs:
                      president:
                        color: red
                """);

        assertThat(snapshot.job(JobId.of("government", "president")).orElseThrow().color())
                .isEqualTo("<red>");
        assertThat(snapshot.type("government").orElseThrow().hasColor()).isFalse();
    }

    @Test
    void aMalformedColourIsReportedAndIgnoredRatherThanLeakingAsText() {
        JobSnapshot snapshot = snapshotOf("""
                types:
                  government:
                    color: nonsense
                    jobs:
                      president: {}
                """);

        assertThat(snapshot.type("government").orElseThrow().hasColor()).isFalse();
        assertThat(logged).anySatisfy(record ->
                assertThat(record.getMessage()).contains("neither a named MiniMessage colour"));
    }

    @Test
    void aMalformedJobColourFallsBackToItsTypeRatherThanToNothing() {
        JobSnapshot snapshot = snapshotOf("""
                types:
                  government:
                    color: gold
                    jobs:
                      president:
                        color: "#zzzzzz"
                """);

        assertThat(snapshot.job(JobId.of("government", "president")).orElseThrow().color())
                .isEqualTo("<gold>");
    }

    @Test
    void theShippedFileGivesEveryTypeAColour() throws Exception {
        try (var in = getClass().getResourceAsStream("/jobs.yml")) {
            var config = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            JobSnapshot snapshot = JobSnapshot.build(JobsYaml.parse(config), log);

            assertThat(snapshot.types()).allSatisfy(type ->
                    assertThat(type.hasColor()).as(type.key() + " should ship with a colour").isTrue());
            // And none of them tripped the malformed-colour warning.
            assertThat(logged).noneSatisfy(record ->
                    assertThat(record.getLevel()).isEqualTo(Level.WARNING));
        }
    }
}
