-- Jobs, licences and qualifications (:jobs plugin).
--
-- LuckPerms is the SOURCE OF TRUTH for who holds what. Neither table here is
-- authoritative, and neither should ever be read as "the grant":
--
--   job_event      Append-only audit log of every membership TRANSITION, whether it
--                  came from /hire, /fire, /quit, the JobsApi, or was discovered
--                  after the fact by the reconciler. A no-op (hiring someone who
--                  already holds the job) writes nothing — the log records changes,
--                  not attempts, so an external plugin's periodic re-sync is free.
--
--   job_membership A mirror of LuckPerms' current membership of the configured job
--                  groups. It exists so the reconciler can diff cheaply and so
--                  rosters/reporting can be queried without touching LuckPerms.
--                  Rebuilt from LuckPerms on every reconciliation tick.
--
-- Actors are NOT always players. Trades are granted by another plugin through the
-- JobsApi, the console can hire anyone, and the reconciler records drift it merely
-- observed. So actor identity is modelled as a type plus an optional uuid/name
-- rather than a NOT NULL BINARY(16).
--
-- No foreign key to economy_players: that table is a login-populated directory
-- cache, and the reconciler legitimately observes UUIDs that have never joined this
-- server (LuckPerms data imported from elsewhere). A FK would make those events
-- unloggable, which is the opposite of what an audit log is for.
--
-- Deliberately NOT modelled, and not needed by anything today: job expiry (an
-- expires_at column) and single-holder offices (a max-holders constraint). Both are
-- additive ALTERs against an append-only log and a rebuildable mirror, so there is
-- no cost to leaving them out until they are actually wanted.

CREATE TABLE job_event (
    event_id         BIGINT       NOT NULL AUTO_INCREMENT,

    -- The identity AS IT WAS at the time of the event. Denormalised on purpose:
    -- jobs.yml can rename a job or re-point it at a different LuckPerms group, and
    -- history must stay truthful about what actually happened.
    type_key         VARCHAR(64)  NOT NULL,
    job_key          VARCHAR(64)  NOT NULL,
    group_name       VARCHAR(64)  NOT NULL,

    subject_uuid_bin BINARY(16)   NOT NULL,

    action           ENUM('HIRE','FIRE','QUIT','DETECTED_ADD','DETECTED_REMOVE') NOT NULL,

    -- The channel the change arrived through; mirrors the spirit of
    -- explorer_group_member.source (V10).
    source           ENUM('COMMAND','API','RECONCILER') NOT NULL,

    actor_type       ENUM('PLAYER','CONSOLE','PLUGIN','SYSTEM') NOT NULL,
    -- Non-NULL only for PLAYER. CONSOLE and SYSTEM carry no identity.
    actor_uuid_bin   BINARY(16)   NULL,
    -- Plugin name for PLUGIN; a name snapshot for PLAYER (names change, UUIDs don't).
    actor_name       VARCHAR(64)  NULL,

    -- 1 when the can-manage hierarchy was bypassed: jobs.admin, console, a plugin,
    -- or the reconciler. Lets an auditor find every privileged grant in one query.
    via_admin        TINYINT(1)   NOT NULL DEFAULT 0,

    reason           VARCHAR(255) NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (event_id),

    -- Ordering is by event_id, not created_at: TIMESTAMP has one-second resolution
    -- and two events in the same second must still be totally ordered.
    KEY ix_job_event_subject (subject_uuid_bin, event_id),
    KEY ix_job_event_job     (type_key, job_key, event_id),
    KEY ix_job_event_actor   (actor_uuid_bin, event_id),

    CONSTRAINT ck_job_event_actor CHECK (
        (actor_type =  'PLAYER' AND actor_uuid_bin IS NOT NULL) OR
        (actor_type <> 'PLAYER' AND actor_uuid_bin IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_membership (
    subject_uuid_bin BINARY(16)  NOT NULL,
    type_key         VARCHAR(64) NOT NULL,
    job_key          VARCHAR(64) NOT NULL,
    group_name       VARCHAR(64) NOT NULL,

    -- 'jobs'     granted through this plugin (/hire, /quit or the JobsApi).
    -- 'external' found in LuckPerms by the reconciler with no matching grant, i.e.
    --            another plugin or an operator ran `lp user ... parent add`.
    --            RECORDED, NEVER STRIPPED: trades are expected to arrive this way,
    --            and "correcting" them would have two plugins write-fighting on a
    --            30-minute loop. Query source='external' to find candidates for
    --            migration onto the JobsApi.
    source           ENUM('jobs','external') NOT NULL DEFAULT 'jobs',

    granted_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_verified_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
                         ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (subject_uuid_bin, type_key, job_key),
    KEY ix_job_membership_job    (type_key, job_key),
    KEY ix_job_membership_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
