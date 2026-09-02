package io.paradaux.jobs.services;

import io.paradaux.jobs.model.ListingCommand;
import io.paradaux.jobs.model.config.JobsYaml;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code jobs.yml} is the source of truth for which top-level listing commands
 * exist. These cover the resolution step — what the registrar is handed — since the
 * registration itself needs a live server command map.
 */
class ListingCommandResolutionTest {

    private Logger log;
    private List<LogRecord> logged;

    @BeforeEach
    void setUp() {
        logged = new ArrayList<>();
        log = Logger.getLogger("ListingCommandResolutionTest-" + System.nanoTime());
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

    private boolean warned(String fragment) {
        return logged.stream().anyMatch(r -> r.getLevel().intValue() >= Level.WARNING.intValue()
                && r.getMessage() != null && r.getMessage().contains(fragment));
    }

    private static final String TYPES = """
            types:
              licenses: { jobs: {} }
              qualifications: { jobs: {} }
              trades: { jobs: {} }
            """;

    @Test
    void theCommandNameComesFromTheConfigKey() {
        // Declaring `qual:` is what creates /qual — no code change involved.
        JobSnapshot snapshot = snapshotOf("""
                listing-commands:
                  qual:
                    type: qualifications
                    aliases: [ quals ]
                """ + TYPES);

        assertThat(snapshot.listingCommands()).singleElement()
                .satisfies(command -> {
                    assertThat(command.name()).isEqualTo("qual");
                    assertThat(command.typeKey()).isEqualTo("qualifications");
                    assertThat(command.aliases()).containsExactly("quals");
                    assertThat(command.allNames()).containsExactly("qual", "quals");
                });
    }

    @Test
    void aliasesResolveToTheSameType() {
        JobSnapshot snapshot = snapshotOf("""
                listing-commands:
                  licenses:
                    type: licenses
                    aliases: [ licence, licences ]
                """ + TYPES);

        assertThat(snapshot.listingType("licenses")).contains("licenses");
        assertThat(snapshot.listingType("licence")).contains("licenses");
        assertThat(snapshot.listingType("LICENCES")).contains("licenses");
        assertThat(snapshot.listingType("nope")).isEmpty();
    }

    @Test
    void removingAnEntryRemovesTheCommand() {
        // The other half of "jobs.yml is the source of truth": absence means absence.
        assertThat(snapshotOf("listing-commands: {}\n" + TYPES).listingCommands()).isEmpty();
        assertThat(snapshotOf(TYPES).listingCommands()).isEmpty();
    }

    @Test
    void anEntryNamingAnUnconfiguredTypeIsDroppedWithAWarning() {
        // Registering a root that could only ever answer "not configured" would be
        // worse than not registering it.
        JobSnapshot snapshot = snapshotOf("""
                listing-commands:
                  ghost:
                    type: nonexistent
                """ + TYPES);

        assertThat(snapshot.listingCommands()).isEmpty();
        assertThat(warned("will not be registered")).isTrue();
    }

    @Test
    void anAliasCollidingWithAnotherCommandIsDropped() {
        JobSnapshot snapshot = snapshotOf("""
                listing-commands:
                  licenses:
                    type: licenses
                    aliases: [ shared ]
                  qual:
                    type: qualifications
                    aliases: [ shared, quals ]
                """ + TYPES);

        // First declaration wins the shared alias; the second keeps its own names.
        assertThat(snapshot.listingType("shared")).contains("licenses");
        assertThat(snapshot.listingCommands()).filteredOn(c -> c.name().equals("qual"))
                .singleElement()
                .satisfies(c -> assertThat(c.aliases()).containsExactly("quals"));
    }

    @Test
    void anAliasEqualToItsOwnNameIsNotDuplicated() {
        JobSnapshot snapshot = snapshotOf("""
                listing-commands:
                  qual:
                    type: qualifications
                    aliases: [ qual, quals ]
                """ + TYPES);

        assertThat(snapshot.listingCommands()).singleElement()
                .satisfies(c -> assertThat(c.allNames()).containsExactly("qual", "quals"));
    }

    @Test
    void theShorthandFormStillWorks() {
        // `licenses: licenses` — an older file, or an entry that needs no aliases.
        JobSnapshot snapshot = snapshotOf("listing-commands:\n  licenses: licenses\n" + TYPES);

        assertThat(snapshot.listingCommands()).singleElement()
                .satisfies(c -> {
                    assertThat(c.name()).isEqualTo("licenses");
                    assertThat(c.typeKey()).isEqualTo("licenses");
                    assertThat(c.aliases()).isEmpty();
                });
    }

    @Test
    void namesAndTypesAreCaseInsensitive() {
        JobSnapshot snapshot = snapshotOf("""
                listing-commands:
                  QUAL:
                    type: Qualifications
                    aliases: [ Quals ]
                """ + TYPES);

        assertThat(snapshot.listingCommands()).singleElement()
                .satisfies(c -> {
                    assertThat(c.name()).isEqualTo("qual");
                    assertThat(c.typeKey()).isEqualTo("qualifications");
                    assertThat(c.aliases()).containsExactly("quals");
                });
    }

    @Test
    void everyTypeStaysReachableWithoutAListingCommand() {
        // The generic route is why an entry here is optional rather than required.
        JobSnapshot snapshot = snapshotOf(TYPES);
        assertThat(snapshot.type("trades")).isPresent();
        assertThat(snapshot.listingCommands()).isEmpty();
    }
}
