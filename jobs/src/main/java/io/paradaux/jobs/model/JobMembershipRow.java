package io.paradaux.jobs.model;

import java.sql.Timestamp;

/**
 * One row of {@code job_membership} — the mirror of LuckPerms, never authoritative.
 *
 * <p>{@code source} records provenance: {@code jobs} for a grant this plugin made,
 * {@code external} for membership the reconciler found in LuckPerms with no matching
 * grant. External rows are recorded and never stripped, which is what lets another
 * plugin own a whole job type without the two fighting.</p>
 */
public final class JobMembershipRow {

    private String subjectUuid;
    private String typeKey;
    private String jobKey;
    private String groupName;
    private String source;
    private Timestamp grantedAt;
    private Timestamp lastVerifiedAt;

    public String getSubjectUuid() { return subjectUuid; }
    public void setSubjectUuid(String subjectUuid) { this.subjectUuid = subjectUuid; }

    public String getTypeKey() { return typeKey; }
    public void setTypeKey(String typeKey) { this.typeKey = typeKey; }

    public String getJobKey() { return jobKey; }
    public void setJobKey(String jobKey) { this.jobKey = jobKey; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Timestamp getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Timestamp grantedAt) { this.grantedAt = grantedAt; }

    public Timestamp getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(Timestamp lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }
}
