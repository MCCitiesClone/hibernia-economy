package io.paradaux.jobs.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.jobs.api.ActorType;
import io.paradaux.jobs.api.JobActor;
import io.paradaux.jobs.api.model.JobDefinition;
import io.paradaux.jobs.mappers.JobEventMapper;
import io.paradaux.jobs.mappers.JobMembershipMapper;
import io.paradaux.jobs.model.JobEventRow;
import io.paradaux.jobs.model.JobMembershipRow;

import java.util.List;
import java.util.UUID;

/**
 * Owns the two job tables. The only class that touches their mappers
 * (plugin-architecture/0005).
 *
 * <p>Records <em>transitions</em>, never attempts: a hire that finds the player
 * already holding the job writes nothing at all. That keeps a periodic re-sync from
 * an owning plugin free of log noise, and makes the event stream a faithful history
 * of what actually changed.</p>
 */
@Singleton
public final class JobAuditService {

    /** What kind of change happened. */
    public enum Action {
        HIRE, FIRE, QUIT, DETECTED_ADD, DETECTED_REMOVE
    }

    /** Which entry point the change arrived through. */
    public enum Source {
        COMMAND, API, RECONCILER
    }

    /** Provenance of a mirror row. */
    public enum Provenance {
        JOBS("jobs"), EXTERNAL("external");

        private final String value;

        Provenance(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private final JobEventMapper events;
    private final JobMembershipMapper memberships;

    @Inject
    public JobAuditService(JobEventMapper events, JobMembershipMapper memberships) {
        this.events = events;
        this.memberships = memberships;
    }

    /** Log a transition and bring the mirror into line with it, in that order. */
    public void recordGrant(JobDefinition job, UUID subject, JobActor actor,
                            Action action, Source source, String reason) {
        writeEvent(job, subject, actor, action, source, reason);
        memberships.upsert(subject.toString(), job.id().type(), job.id().job(),
                job.group(), Provenance.JOBS.value());
    }

    /** Log a removal and drop the mirror row. */
    public void recordRevoke(JobDefinition job, UUID subject, JobActor actor,
                             Action action, Source source, String reason) {
        writeEvent(job, subject, actor, action, source, reason);
        memberships.delete(subject.toString(), job.id().type(), job.id().job());
    }

    /**
     * Record membership the reconciler found in LuckPerms that this plugin did not
     * grant — another plugin, or an operator running {@code lp user ... parent add}.
     *
     * <p>Marked {@code external} and never stripped from LuckPerms. Stripping it
     * would put this plugin in a write-fight with whatever granted it, which for a
     * type owned by another plugin would revoke players' jobs at random.</p>
     */
    public void recordDetectedAdd(JobDefinition job, UUID subject) {
        writeEvent(job, subject, JobActor.system(), Action.DETECTED_ADD, Source.RECONCILER, null);
        memberships.upsert(subject.toString(), job.id().type(), job.id().job(),
                job.group(), Provenance.EXTERNAL.value());
    }

    /** Record that a mirrored membership is no longer present in LuckPerms. */
    public void recordDetectedRemove(JobDefinition job, UUID subject) {
        writeEvent(job, subject, JobActor.system(), Action.DETECTED_REMOVE, Source.RECONCILER, null);
        memberships.delete(subject.toString(), job.id().type(), job.id().job());
    }

    /** Refresh {@code last_verified_at} for a membership the reconciler confirmed. */
    public void touch(JobDefinition job, UUID subject, Provenance provenance) {
        memberships.upsert(subject.toString(), job.id().type(), job.id().job(),
                job.group(), provenance.value());
    }

    public List<String> mirroredSubjects(JobDefinition job) {
        return memberships.listSubjects(job.id().type(), job.id().job());
    }

    public List<JobEventRow> history(UUID subject, int limit) {
        return events.recentForSubject(subject.toString(), limit);
    }

    public List<JobMembershipRow> externalMemberships(int limit) {
        return memberships.listExternal(limit);
    }

    public int externalCount() {
        return memberships.countExternal();
    }

    private void writeEvent(JobDefinition job, UUID subject, JobActor actor,
                            Action action, Source source, String reason) {
        // Only a PLAYER actor carries a uuid; the table's CHECK constraint enforces it.
        String actorUuid = actor.type() == ActorType.PLAYER && actor.uuid() != null
                ? actor.uuid().toString() : null;
        events.record(job.id().type(), job.id().job(), job.group(), subject.toString(),
                action.name(), source.name(), actor.type().name(), actorUuid,
                actor.name(), actor.privileged(), trim(reason));
    }

    /** The column is VARCHAR(255); truncate rather than let the insert fail. */
    private static String trim(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }
}
