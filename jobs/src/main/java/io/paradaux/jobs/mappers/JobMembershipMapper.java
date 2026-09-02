package io.paradaux.jobs.mappers;

import io.paradaux.jobs.model.JobMembershipRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * The LuckPerms mirror. Never authoritative — read it for rosters and reporting, but
 * never as proof that someone holds a job.
 */
public interface JobMembershipMapper {

    /**
     * Record or refresh a membership.
     *
     * <p>On conflict only {@code last_verified_at} moves: {@code granted_at} keeps
     * the original grant time, and {@code source} is sticky, so a reconciler pass
     * over a row this plugin created never relabels it as external.</p>
     */
    @Insert("""
            INSERT INTO job_membership (subject_uuid_bin, type_key, job_key, group_name, source)
            VALUES (uuid_to_bin(#{subjectUuid}), #{typeKey}, #{jobKey}, #{groupName}, #{source})
            ON DUPLICATE KEY UPDATE last_verified_at = CURRENT_TIMESTAMP,
                                    group_name = VALUES(group_name)
            """)
    int upsert(@Param("subjectUuid") String subjectUuid,
               @Param("typeKey") String typeKey,
               @Param("jobKey") String jobKey,
               @Param("groupName") String groupName,
               @Param("source") String source);

    @Delete("""
            DELETE FROM job_membership
             WHERE subject_uuid_bin = uuid_to_bin(#{subjectUuid})
               AND type_key = #{typeKey} AND job_key = #{jobKey}
            """)
    int delete(@Param("subjectUuid") String subjectUuid,
               @Param("typeKey") String typeKey,
               @Param("jobKey") String jobKey);

    /** Everyone the mirror believes holds this job — the reconciler's left-hand side. */
    @Select("""
            SELECT bin_to_uuid(subject_uuid_bin)
              FROM job_membership
             WHERE type_key = #{typeKey} AND job_key = #{jobKey}
            """)
    List<String> listSubjects(@Param("typeKey") String typeKey, @Param("jobKey") String jobKey);

    @Select("""
            SELECT bin_to_uuid(subject_uuid_bin) AS subject_uuid, type_key, job_key,
                   group_name, source, granted_at, last_verified_at
              FROM job_membership
             WHERE subject_uuid_bin = uuid_to_bin(#{subjectUuid})
            """)
    List<JobMembershipRow> listForSubject(@Param("subjectUuid") String subjectUuid);

    /**
     * Memberships granted outside this plugin, newest first.
     *
     * <p>Backs {@code /jobs audit external}: these are the candidates for moving an
     * owning plugin onto the JobsApi, which would give them proper audit rows.</p>
     */
    @Select("""
            SELECT bin_to_uuid(subject_uuid_bin) AS subject_uuid, type_key, job_key,
                   group_name, source, granted_at, last_verified_at
              FROM job_membership
             WHERE source = 'external'
             ORDER BY granted_at DESC
             LIMIT #{limit}
            """)
    List<JobMembershipRow> listExternal(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM job_membership WHERE source = 'external'")
    int countExternal();
}
