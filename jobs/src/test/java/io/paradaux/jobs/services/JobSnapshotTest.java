package io.paradaux.jobs.services;

import io.paradaux.jobs.api.model.JobId;
import io.paradaux.jobs.api.model.JobType;
import io.paradaux.jobs.model.config.JobsSettings;
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
 * Builds snapshots from real YAML text rather than hand-built records, so the parser
 * and the indexer are exercised together — a mismatch between them is exactly the
 * class of bug that would otherwise only surface on a live server boot.
 */
class JobSnapshotTest {

    private Logger log;
    private List<LogRecord> logged;

    @BeforeEach
    void setUp() {
        logged = new ArrayList<>();
        log = Logger.getLogger("JobSnapshotTest-" + System.nanoTime());
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logged.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
    }

    private JobSnapshot snapshotOf(String yaml) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        JobsSettings settings = JobsYaml.parse(config);
        return JobSnapshot.build(settings, log);
    }

    private boolean loggedAtLeast(Level level, String fragment) {
        return logged.stream()
                .anyMatch(r -> r.getLevel().intValue() >= level.intValue()
                        && r.getMessage() != null && r.getMessage().contains(fragment));
    }

    private static final String TYPICAL = """
            admin-permission: "staff.jobs"
            provision-groups: false
            show-empty-types: true
            listing-commands:
              licenses: licenses
              qualifications: quals
            reconciliation:
              enabled: false
              interval-seconds: 60
            types:
              licenses:
                display-name: "Licences"
                order: 50
                jobs:
                  firearms:
                    display-name: "Firearms Licence"
                    group: "license-firearms"
                    description: "Permits a firearm."
              quals:
                display-name: "Qualifications"
                order: 60
                jobs:
                  bar-exam: {}
              government:
                display-name: "Government"
                order: 10
                can-manage: [ "licenses/*" ]
                jobs:
                  president:
                    display-name: "President"
                    group: "president"
                    can-manage: [ "government/*" ]
                  commerce-clerk: {}
            """;

    @Test
    void typesAreOrderedByConfiguredOrder() {
        JobSnapshot snapshot = snapshotOf(TYPICAL);
        assertThat(snapshot.types()).extracting(JobType::key)
                .containsExactly("government", "licenses", "quals");
    }

    @Test
    void unsetDisplayNameFallsBackToATitleCasedKey() {
        JobSnapshot snapshot = snapshotOf(TYPICAL);
        assertThat(snapshot.job(JobId.of("government", "commerce-clerk")).orElseThrow().displayName())
                .isEqualTo("Commerce Clerk");
    }

    @Test
    void unsetGroupDefaultsToTheJobKey() {
        JobSnapshot snapshot = snapshotOf(TYPICAL);
        assertThat(snapshot.job(JobId.of("quals", "bar-exam")).orElseThrow().group())
                .isEqualTo("bar-exam");
    }

    @Test
    void reverseGroupIndexResolvesLuckPermsGroupsToJobs() {
        JobSnapshot snapshot = snapshotOf(TYPICAL);
        // This is what turns a player's LuckPerms group set into their held jobs.
        assertThat(snapshot.byGroup("license-firearms")).contains(JobId.of("licenses", "firearms"));
        assertThat(snapshot.byGroup("LICENSE-FIREARMS")).contains(JobId.of("licenses", "firearms"));
        assertThat(snapshot.byGroup("not-a-job-group")).isEmpty();
    }

    @Test
    void typeSelectorsAreUnionedIntoEveryJobOfThatType() {
        JobSnapshot snapshot = snapshotOf(TYPICAL);
        // president declares government/*, and government declares licenses/* for all.
        assertThat(snapshot.job(JobId.of("government", "president")).orElseThrow().canManage())
                .containsExactlyInAnyOrder("government/*", "licenses/*");
        // The clerk declares nothing of its own but still inherits the type's.
        assertThat(snapshot.job(JobId.of("government", "commerce-clerk")).orElseThrow().canManage())
                .containsExactly("licenses/*");
    }

    @Test
    void bareKeysResolveOnlyWhenUnambiguous() {
        JobSnapshot snapshot = snapshotOf(TYPICAL);
        assertThat(snapshot.parse("president")).contains(JobId.of("government", "president"));
        assertThat(snapshot.parse("government/president")).contains(JobId.of("government", "president"));
        assertThat(snapshot.parse("nope")).isEmpty();
    }

    @Test
    void aBareKeyDefinedInTwoTypesIsAmbiguousRatherThanGuessed() {
        JobSnapshot snapshot = snapshotOf("""
                types:
                  trades:
                    jobs:
                      inspector: { group: trade-inspector }
                  government:
                    jobs:
                      inspector: { group: gov-inspector }
                """);
        assertThat(snapshot.parse("inspector")).isEmpty();
        assertThat(snapshot.isAmbiguousBareKey("inspector")).isTrue();
        // Qualifying it disambiguates.
        assertThat(snapshot.parse("trades/inspector")).contains(JobId.of("trades", "inspector"));
    }

    @Test
    void twoJobsClaimingOneGroupIsReportedAndTheFirstWins() {
        JobSnapshot snapshot = snapshotOf("""
                types:
                  a:
                    jobs:
                      one: { group: shared }
                  b:
                    jobs:
                      two: { group: shared }
                """);
        assertThat(snapshot.byGroup("shared")).contains(JobId.of("a", "one"));
        assertThat(snapshot.job(JobId.of("b", "two"))).isEmpty();
        assertThat(loggedAtLeast(Level.SEVERE, "is claimed by both")).isTrue();
    }

    @Test
    void listingCommandsResolveToTypeKeys() {
        JobSnapshot snapshot = snapshotOf(TYPICAL);
        assertThat(snapshot.listingType("licenses")).contains("licenses");
        // The command name and the type key need not agree — that indirection is the
        // point, so renaming a type cannot break /qualifications.
        assertThat(snapshot.listingType("qualifications")).contains("quals");
    }

    @Test
    void aListingCommandNamingAnUnknownTypeIsReportedAndDisabled() {
        JobSnapshot snapshot = snapshotOf("""
                listing-commands:
                  licenses: nonexistent
                types:
                  government:
                    jobs: {}
                """);
        assertThat(snapshot.listingType("licenses")).isEmpty();
        assertThat(loggedAtLeast(Level.WARNING, "is not configured")).isTrue();
    }

    @Test
    void malformedSelectorsAreDroppedAndReported() {
        JobSnapshot snapshot = snapshotOf("""
                types:
                  government:
                    jobs:
                      president:
                        can-manage: [ "government/senator", "not a selector" ]
                      senator: {}
                """);
        assertThat(snapshot.job(JobId.of("government", "president")).orElseThrow().canManage())
                .containsExactly("government/senator");
        assertThat(loggedAtLeast(Level.WARNING, "malformed can-manage selector")).isTrue();
    }

    @Test
    void aConcreteSelectorNamingAnUnknownJobIsReported() {
        snapshotOf("""
                types:
                  government:
                    jobs:
                      president:
                        can-manage: [ "government/ghost" ]
                """);
        assertThat(loggedAtLeast(Level.WARNING, "names a job that is not configured")).isTrue();
    }

    @Test
    void suggestionsOfferTheBareJobKeyNotTheQualifiedForm() {
        JobSnapshot snapshot = snapshotOf(TYPICAL);
        // What a player types and reads is "president", not "government/president".
        // The type is an organisational detail of jobs.yml, not part of the name.
        assertThat(snapshot.suggestions())
                .contains("president", "firearms", "commerce-clerk")
                .doesNotContain("government/president", "licenses/firearms")
                .isSorted();
    }

    @Test
    void onlyAnAmbiguousKeyFallsBackToTheQualifiedForm() {
        JobSnapshot snapshot = snapshotOf("""
                types:
                  trades:
                    jobs:
                      inspector: { group: trade-inspector }
                      electrician: { group: trade-electrician }
                  government:
                    jobs:
                      inspector: { group: gov-inspector }
                """);
        // 'inspector' alone could mean either, so it must be qualified to be usable;
        // 'electrician' is unique and stays bare.
        assertThat(snapshot.suggestions())
                .containsExactly("electrician", "government/inspector", "trades/inspector");
    }

    @Test
    void bothFormsStillResolveEvenThoughOnlyOneIsSuggested() {
        JobSnapshot snapshot = snapshotOf(TYPICAL);
        // Narrowing the suggestions must not narrow what a player may type.
        assertThat(snapshot.parse("president")).contains(JobId.of("government", "president"));
        assertThat(snapshot.parse("government/president"))
                .contains(JobId.of("government", "president"));
    }

    @Test
    void topLevelFlagsAreRead() {
        JobSnapshot snapshot = snapshotOf(TYPICAL);
        assertThat(snapshot.adminPermission()).isEqualTo("staff.jobs");
        assertThat(snapshot.provisionGroups()).isFalse();
        assertThat(snapshot.showEmptyTypes()).isTrue();
        assertThat(snapshot.allGroups()).contains("president", "license-firearms", "bar-exam");
    }

    @Test
    void anEmptySnapshotIsUsableRatherThanNull() {
        JobSnapshot empty = JobSnapshot.empty();
        assertThat(empty.types()).isEmpty();
        assertThat(empty.jobs()).isEmpty();
        assertThat(empty.parse("anything")).isEmpty();
        assertThat(empty.suggestions()).isEmpty();
        assertThat(empty.adminPermission()).isEqualTo("jobs.admin");
    }

    @Test
    void missingSettingsYieldsAnEmptySnapshotAndAnError() {
        assertThat(JobSnapshot.build(null, log).types()).isEmpty();
        assertThat(loggedAtLeast(Level.SEVERE, "could not be loaded")).isTrue();
    }
}
