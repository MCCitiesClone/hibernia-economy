-- Explorer group-based RBAC (extends V3 explorer_auth).
--
-- Moves the explorer from flat per-player role grants (explorer_role) toward a
-- group + capability model:
--   * explorer_group            — a named group; optionally fed from a LuckPerms node
--   * explorer_group_capability — the capabilities a group grants (e.g. staff.audit)
--   * explorer_group_member     — player <-> group membership, by source
--
-- A player's effective capabilities are the union of every group they belong to
-- (plus the legacy explorer_role rows, mapped to capabilities in the explorer).
-- explorer_role is kept as-is for backward compatibility.
--
-- Membership is either 'manual' (an admin added the player in the explorer UI) or
-- 'luckperms' (reconciled from the group's luckperms_node by the treasury-api
-- plugin's reconciliation cron). The cron only ever touches its own 'luckperms'
-- rows, so manual grants are never clobbered.

-- A custom group. luckperms_node is the LuckPerms group name (or permission node)
-- whose members this group mirrors; NULL means the group is manual-only.
CREATE TABLE explorer_group (
    group_id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name                VARCHAR(64)  NOT NULL,
    description         VARCHAR(255),
    luckperms_node      VARCHAR(128),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_uuid_bin BINARY(16),
    PRIMARY KEY (group_id),
    UNIQUE KEY uq_group_name (name),
    KEY idx_group_luckperms (luckperms_node)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The capabilities a group grants. capability is a free-form string from the
-- explorer's vocabulary (admin | staff.audit | government | ...). Kept as a
-- separate row-per-capability table so a group can grant several.
CREATE TABLE explorer_group_capability (
    group_id   INT UNSIGNED NOT NULL,
    capability VARCHAR(64)  NOT NULL,
    PRIMARY KEY (group_id, capability),
    CONSTRAINT fk_group_capability_group FOREIGN KEY (group_id)
        REFERENCES explorer_group (group_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Player membership in a group. source distinguishes admin-added ('manual') from
-- cron-reconciled ('luckperms') rows so the reconciliation job can add/remove its
-- own rows without disturbing manual grants.
CREATE TABLE explorer_group_member (
    group_id          INT UNSIGNED NOT NULL,
    player_uuid_bin   BINARY(16)   NOT NULL,
    source            ENUM('manual','luckperms') NOT NULL DEFAULT 'manual',
    added_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by_uuid_bin BINARY(16),
    PRIMARY KEY (group_id, player_uuid_bin),
    KEY idx_group_member_player (player_uuid_bin),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id)
        REFERENCES explorer_group (group_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
