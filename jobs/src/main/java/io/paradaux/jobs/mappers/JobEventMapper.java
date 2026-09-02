package io.paradaux.jobs.mappers;

import io.paradaux.jobs.model.JobEventRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * The append-only job audit log.
 *
 * <p>Only services may use this (plugin-architecture/0005). UUIDs cross the boundary
 * as {@code CHAR(36)} strings and are converted by the database's own
 * {@code uuid_to_bin}/{@code bin_to_uuid} functions, matching every other mapper in
 * the monorepo.</p>
 */
public interface JobEventMapper {

    /**
     * Record a transition. {@code actorUuid} is null for every non-player actor —
     * the console, an owning plugin, and the reconciler — which the table's
     * {@code ck_job_event_actor} constraint enforces.
     */
    @Insert("""
            INSERT INTO job_event (type_key, job_key, group_name, subject_uuid_bin,
                                   action, source, actor_type, actor_uuid_bin,
                                   actor_name, via_admin, reason)
            VALUES (#{typeKey}, #{jobKey}, #{groupName}, uuid_to_bin(#{subjectUuid}),
                    #{action}, #{source}, #{actorType},
                    CASE WHEN #{actorUuid} IS NULL THEN NULL ELSE uuid_to_bin(#{actorUuid}) END,
                    #{actorName}, #{viaAdmin}, #{reason})
            """)
    int record(@Param("typeKey") String typeKey,
               @Param("jobKey") String jobKey,
               @Param("groupName") String groupName,
               @Param("subjectUuid") String subjectUuid,
               @Param("action") String action,
               @Param("source") String source,
               @Param("actorType") String actorType,
               @Param("actorUuid") String actorUuid,
               @Param("actorName") String actorName,
               @Param("viaAdmin") boolean viaAdmin,
               @Param("reason") String reason);

    /**
     * A player's recent history, newest first.
     *
     * <p>Ordered by {@code event_id}, not {@code created_at}: the timestamp has
     * one-second resolution, so two events in the same second would otherwise come
     * back in an arbitrary order.</p>
     */
    @Select("""
            SELECT event_id, type_key, job_key, group_name,
                   bin_to_uuid(subject_uuid_bin) AS subject_uuid,
                   action, source, actor_type,
                   bin_to_uuid(actor_uuid_bin) AS actor_uuid,
                   actor_name, via_admin, reason, created_at
              FROM job_event
             WHERE subject_uuid_bin = uuid_to_bin(#{subjectUuid})
             ORDER BY event_id DESC
             LIMIT #{limit}
            """)
    List<JobEventRow> recentForSubject(@Param("subjectUuid") String subjectUuid,
                                       @Param("limit") int limit);

    /** Recent history for one job, newest first. */
    @Select("""
            SELECT event_id, type_key, job_key, group_name,
                   bin_to_uuid(subject_uuid_bin) AS subject_uuid,
                   action, source, actor_type,
                   bin_to_uuid(actor_uuid_bin) AS actor_uuid,
                   actor_name, via_admin, reason, created_at
              FROM job_event
             WHERE type_key = #{typeKey} AND job_key = #{jobKey}
             ORDER BY event_id DESC
             LIMIT #{limit}
            """)
    List<JobEventRow> recentForJob(@Param("typeKey") String typeKey,
                                   @Param("jobKey") String jobKey,
                                   @Param("limit") int limit);
}
