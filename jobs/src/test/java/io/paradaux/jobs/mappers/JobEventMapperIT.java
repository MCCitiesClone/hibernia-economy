package io.paradaux.jobs.mappers;

import io.paradaux.jobs.model.JobEventRow;
import io.paradaux.jobs.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the audit log against a real MariaDB built from the actual migrations. */
class JobEventMapperIT extends IntegrationTestBase {

    private JobEventMapper events() {
        return mapper(JobEventMapper.class);
    }

    private int record(String action, String source, String actorType,
                       String actorUuid, String actorName, UUID subject, boolean viaAdmin) {
        return events().record("trades", "electrician", "trade-electrician",
                subject.toString(), action, source, actorType, actorUuid, actorName,
                viaAdmin, "because");
    }

    @Test
    void aPlayerActorRoundTripsThroughTheUuidHelpers() {
        UUID subject = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        assertThat(record("HIRE", "COMMAND", "PLAYER", actor.toString(), "evan", subject, false))
                .isEqualTo(1);

        List<JobEventRow> rows = events().recentForSubject(subject.toString(), 10);
        assertThat(rows).hasSize(1);
        JobEventRow row = rows.get(0);
        assertThat(row.getSubjectUuid()).isEqualToIgnoringCase(subject.toString());
        assertThat(row.getActorUuid()).isEqualToIgnoringCase(actor.toString());
        assertThat(row.getActorName()).isEqualTo("evan");
        assertThat(row.getAction()).isEqualTo("HIRE");
        assertThat(row.getSource()).isEqualTo("COMMAND");
        assertThat(row.getActorType()).isEqualTo("PLAYER");
        assertThat(row.isViaAdmin()).isFalse();
        assertThat(row.getReason()).isEqualTo("because");
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    void consolePluginAndSystemActorsStoreNoUuid() {
        UUID subject = UUID.randomUUID();
        // The three non-player actors, which is the whole reason actor identity is a
        // type plus an optional uuid rather than a NOT NULL column.
        assertThat(record("HIRE", "COMMAND", "CONSOLE", null, "CONSOLE", subject, true)).isEqualTo(1);
        assertThat(record("HIRE", "API", "PLUGIN", null, "Trades", subject, true)).isEqualTo(1);
        assertThat(record("DETECTED_ADD", "RECONCILER", "SYSTEM", null, "SYSTEM", subject, true))
                .isEqualTo(1);

        List<JobEventRow> rows = events().recentForSubject(subject.toString(), 10);
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> assertThat(row.getActorUuid()).isNull());
        assertThat(rows).extracting(JobEventRow::getActorType)
                .containsExactlyInAnyOrder("CONSOLE", "PLUGIN", "SYSTEM");
        assertThat(rows).extracting(JobEventRow::getActorName).contains("Trades");
    }

    @Test
    void theCheckConstraintRejectsAPlayerActorWithNoUuid() {
        // The invariant that keeps "who did this" answerable for every row.
        assertThatThrownBy(() ->
                record("HIRE", "COMMAND", "PLAYER", null, "evan", UUID.randomUUID(), false))
                .rootCause()
                .isInstanceOf(SQLException.class);
    }

    @Test
    void theCheckConstraintRejectsANonPlayerActorCarryingAUuid() {
        assertThatThrownBy(() -> record("HIRE", "COMMAND", "CONSOLE",
                UUID.randomUUID().toString(), "CONSOLE", UUID.randomUUID(), true))
                .rootCause()
                .isInstanceOf(SQLException.class);
    }

    @Test
    void historyIsNewestFirstAndOrderedWithinTheSameSecond() {
        UUID subject = UUID.randomUUID();
        // created_at only has second resolution, so ordering must come from event_id
        // or these three would come back in an arbitrary order.
        record("HIRE", "COMMAND", "CONSOLE", null, "CONSOLE", subject, true);
        record("FIRE", "COMMAND", "CONSOLE", null, "CONSOLE", subject, true);
        record("HIRE", "API", "PLUGIN", null, "Trades", subject, true);

        List<JobEventRow> rows = events().recentForSubject(subject.toString(), 10);
        assertThat(rows).extracting(JobEventRow::getEventId).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
        assertThat(rows.get(0).getSource()).isEqualTo("API");
    }

    @Test
    void historyIsLimitedAndScopedToTheSubject() {
        UUID subject = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            record("HIRE", "COMMAND", "CONSOLE", null, "CONSOLE", subject, true);
        }
        record("HIRE", "COMMAND", "CONSOLE", null, "CONSOLE", other, true);

        assertThat(events().recentForSubject(subject.toString(), 3)).hasSize(3);
        assertThat(events().recentForSubject(other.toString(), 10)).hasSize(1);
        assertThat(events().recentForSubject(UUID.randomUUID().toString(), 10)).isEmpty();
    }

    @Test
    void perJobHistoryIsScopedToThatJob() {
        UUID subject = UUID.randomUUID();
        record("HIRE", "COMMAND", "CONSOLE", null, "CONSOLE", subject, true);
        events().record("government", "president", "president", subject.toString(),
                "HIRE", "COMMAND", "CONSOLE", null, "CONSOLE", true, null);

        assertThat(events().recentForJob("trades", "electrician", 10)).hasSize(1);
        assertThat(events().recentForJob("government", "president", 10)).hasSize(1);
        assertThat(events().recentForJob("legal", "justice", 10)).isEmpty();
    }

    @Test
    void aNullReasonIsStored() {
        UUID subject = UUID.randomUUID();
        events().record("trades", "electrician", "trade-electrician", subject.toString(),
                "QUIT", "COMMAND", "PLAYER", subject.toString(), "evan", false, null);

        assertThat(events().recentForSubject(subject.toString(), 1).get(0).getReason()).isNull();
    }
}
