-- Explorer authentication & RBAC (see treasury-rest-api/AUTH-SPEC.md).
--
-- Adds the tables backing Keycloak-OIDC login for the ledger explorer:
--   * explorer_identity  — keycloak_sub -> Minecraft player link (durable record)
--   * explorer_role      — per-player elevated role grants (admin/government)
--   * explorer_link_code — short-lived codes for the in-game linking flow
--   * explorer_audit     — "who looked at what" access log
--
-- These live in each server's own economy DB; the per-instance DB is the tenant
-- boundary, so no server_id discriminator is needed. Baseline "player" access
-- (see your own data) is implicit for any linked identity and is NOT stored.

-- Keycloak subject -> Minecraft account. The sub is global (one shared realm);
-- the row is upserted from the `minecraft_uuid` token claim on first request, or
-- written by the in-game linking command (treasury-api-plugin).
CREATE TABLE explorer_identity (
    keycloak_sub    VARCHAR(64)  NOT NULL,
    player_uuid_bin BINARY(16)   NOT NULL,
    minecraft_name  VARCHAR(32),
    linked_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    linked_by       VARCHAR(64),
    PRIMARY KEY (keycloak_sub),
    KEY idx_identity_player (player_uuid_bin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Elevated role grants, keyed by Minecraft UUID. Managed in-game via
-- `/treasuryapi ui user add|remove <role> <player>`. role is one of
-- 'admin' | 'government'. 'player' is implicit and never stored here.
CREATE TABLE explorer_role (
    player_uuid_bin     BINARY(16)  NOT NULL,
    role                VARCHAR(32) NOT NULL,
    granted_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by_uuid_bin BINARY(16),
    PRIMARY KEY (player_uuid_bin, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Short-lived codes for the interim in-game linking flow: the web UI issues a
-- code tied to the caller's keycloak_sub; the player redeems it in-game with
-- `/treasuryapi ui link <code>`. Expired rows are swept lazily / by a job.
CREATE TABLE explorer_link_code (
    code           CHAR(8)     NOT NULL,
    keycloak_sub   VARCHAR(64) NOT NULL,
    minecraft_name VARCHAR(32),
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at     TIMESTAMP   NOT NULL,
    PRIMARY KEY (code),
    KEY idx_link_code_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Access log for privacy-sensitive reads (account history, chestshop detail,
-- firm internals, the firehose, any cross-player view) and 403 denials.
-- Anonymous public/aggregate views are NOT logged. Written async, fail-open.
CREATE TABLE explorer_audit (
    audit_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_sub      VARCHAR(64)  NOT NULL,
    actor_uuid_bin BINARY(16),
    actor_name     VARCHAR(32),
    actor_role     VARCHAR(32),               -- effective role used for the access
    method         VARCHAR(8)   NOT NULL,
    path           VARCHAR(255) NOT NULL,
    target_type    VARCHAR(32),               -- account | transaction | firm | player | chestshop | global
    target_id      VARCHAR(64),               -- e.g. account_id, txn_id, firm name
    outcome        SMALLINT     NOT NULL,      -- HTTP status (200 allowed, 403 denied)
    source_ip      VARCHAR(45),               -- from X-Forwarded-For (gateway)
    PRIMARY KEY (audit_id),
    KEY idx_audit_actor  (actor_uuid_bin, at),
    KEY idx_audit_target (target_type, target_id, at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
