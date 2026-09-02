package io.paradaux.jobs.model;

import java.sql.Timestamp;

/**
 * One row of {@code job_event} — an append-only record of a membership transition.
 *
 * <p>The type, job and group are stored as they were at the time, so history stays
 * truthful after a job is renamed, re-pointed at a different LuckPerms group, or
 * removed from the configuration entirely.</p>
 *
 * <p>Mutable with setters because MyBatis populates it by reflection; nothing else
 * writes to it.</p>
 */
public final class JobEventRow {

    private long eventId;
    private String typeKey;
    private String jobKey;
    private String groupName;
    private String subjectUuid;
    private String action;
    private String source;
    private String actorType;
    private String actorUuid;
    private String actorName;
    private boolean viaAdmin;
    private String reason;
    private Timestamp createdAt;

    public long getEventId() { return eventId; }
    public void setEventId(long eventId) { this.eventId = eventId; }

    public String getTypeKey() { return typeKey; }
    public void setTypeKey(String typeKey) { this.typeKey = typeKey; }

    public String getJobKey() { return jobKey; }
    public void setJobKey(String jobKey) { this.jobKey = jobKey; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getSubjectUuid() { return subjectUuid; }
    public void setSubjectUuid(String subjectUuid) { this.subjectUuid = subjectUuid; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getActorUuid() { return actorUuid; }
    public void setActorUuid(String actorUuid) { this.actorUuid = actorUuid; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public boolean isViaAdmin() { return viaAdmin; }
    public void setViaAdmin(boolean viaAdmin) { this.viaAdmin = viaAdmin; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
