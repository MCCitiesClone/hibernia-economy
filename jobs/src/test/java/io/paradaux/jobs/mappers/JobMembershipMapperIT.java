package io.paradaux.jobs.mappers;

import io.paradaux.jobs.model.JobMembershipRow;
import io.paradaux.jobs.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the LuckPerms mirror the reconciler diffs against. */
class JobMembershipMapperIT extends IntegrationTestBase {

    private JobMembershipMapper memberships() {
        return mapper(JobMembershipMapper.class);
    }

    private int upsert(UUID subject, String source) {
        return memberships().upsert(subject.toString(), "trades", "electrician",
                "trade-electrician", source);
    }

    @Test
    void aMembershipRoundTrips() {
        UUID subject = UUID.randomUUID();
        assertThat(upsert(subject, "jobs")).isEqualTo(1);

        List<JobMembershipRow> rows = memberships().listForSubject(subject.toString());
        assertThat(rows).hasSize(1);
        JobMembershipRow row = rows.get(0);
        assertThat(row.getSubjectUuid()).isEqualToIgnoringCase(subject.toString());
        assertThat(row.getTypeKey()).isEqualTo("trades");
        assertThat(row.getJobKey()).isEqualTo("electrician");
        assertThat(row.getGroupName()).isEqualTo("trade-electrician");
        assertThat(row.getSource()).isEqualTo("jobs");
        assertThat(row.getGrantedAt()).isNotNull();
        assertThat(row.getLastVerifiedAt()).isNotNull();
    }

    @Test
    void reUpsertingPreservesTheOriginalGrantTimeAndProvenance() {
        UUID subject = UUID.randomUUID();
        upsert(subject, "jobs");
        JobMembershipRow first = memberships().listForSubject(subject.toString()).get(0);

        // What the reconciler does on every tick for a membership it confirms. It
        // must not look like a fresh grant, and must not relabel our own row as
        // external just because the reconciler was the one that saw it.
        upsert(subject, "external");

        List<JobMembershipRow> rows = memberships().listForSubject(subject.toString());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getGrantedAt()).isEqualTo(first.getGrantedAt());
        assertThat(rows.get(0).getSource()).isEqualTo("jobs");
    }

    @Test
    void theCompositeKeyIsSubjectPlusTypePlusJob() {
        UUID subject = UUID.randomUUID();
        upsert(subject, "jobs");
        memberships().upsert(subject.toString(), "government", "president", "president", "jobs");
        memberships().upsert(subject.toString(), "trades", "plumber", "trade-plumber", "jobs");

        // Same player, three distinct jobs — all coexist.
        assertThat(memberships().listForSubject(subject.toString())).hasSize(3);
    }

    @Test
    void listSubjectsReturnsEveryHolderOfOneJob() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        upsert(first, "jobs");
        upsert(second, "external");
        memberships().upsert(UUID.randomUUID().toString(), "government", "president",
                "president", "jobs");

        assertThat(memberships().listSubjects("trades", "electrician"))
                .extracting(String::toLowerCase)
                .containsExactlyInAnyOrder(first.toString().toLowerCase(),
                        second.toString().toLowerCase());
    }

    @Test
    void deleteRemovesOnlyTheNamedMembership() {
        UUID subject = UUID.randomUUID();
        upsert(subject, "jobs");
        memberships().upsert(subject.toString(), "government", "president", "president", "jobs");

        assertThat(memberships().delete(subject.toString(), "trades", "electrician")).isEqualTo(1);

        assertThat(memberships().listForSubject(subject.toString()))
                .extracting(JobMembershipRow::getJobKey)
                .containsExactly("president");
    }

    @Test
    void deletingSomethingAbsentIsANoOp() {
        assertThat(memberships().delete(UUID.randomUUID().toString(), "trades", "electrician"))
                .isZero();
    }

    @Test
    void externalMembershipsAreListedSeparatelyForAuditing() {
        // Backs /jobs audit external — the signal that some plugin is granting a job
        // group directly instead of going through the JobsApi.
        UUID ours = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        upsert(ours, "jobs");
        memberships().upsert(theirs.toString(), "trades", "plumber", "trade-plumber", "external");

        assertThat(memberships().countExternal()).isEqualTo(1);
        assertThat(memberships().listExternal(10))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getSubjectUuid()).isEqualToIgnoringCase(theirs.toString());
                    assertThat(row.getSource()).isEqualTo("external");
                });
    }

    @Test
    void aRepointedGroupNameIsUpdatedOnTheMirror() {
        // jobs.yml can point a job at a different LuckPerms group; the mirror should
        // follow rather than keep reporting the old one.
        UUID subject = UUID.randomUUID();
        upsert(subject, "jobs");
        memberships().upsert(subject.toString(), "trades", "electrician", "renamed-group", "jobs");

        assertThat(memberships().listForSubject(subject.toString()).get(0).getGroupName())
                .isEqualTo("renamed-group");
    }
}
