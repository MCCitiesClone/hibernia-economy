-- Per-issuer API rate-limit overrides.
--
-- The REST API throttles programmatic traffic per ISSUER (the human who created
-- the key, api_keys.owner_uuid_bin) rather than per key. This table lets an
-- admin raise (or lower) a particular issuer's limits with a single multiplier
-- applied across every endpoint — e.g. a trusted bot operator gets x5.
--
-- Absence of a row means the default multiplier of 1.00 (base limits apply).
-- Managed from the explorer admin dashboard; never touched in-game.
CREATE TABLE api_rate_limit_override (
    owner_uuid_bin  BINARY(16)    NOT NULL,
    multiplier      DECIMAL(6,2)  NOT NULL DEFAULT 1.00,
    note            VARCHAR(255)  NULL,
    updated_by_bin  BINARY(16)    NULL COMMENT 'explorer admin (player UUID) who last changed it',
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_uuid_bin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
