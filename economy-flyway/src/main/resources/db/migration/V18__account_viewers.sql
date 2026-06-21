-- =====================================================================
-- ACCOUNT VIEWERS  (Treasury — read-only access tier)  PAR-237
--
-- A third, lowest access tier below member/authorizer: a *viewer* may read an
-- account's balance and ledger history but cannot spend from it or manage its
-- access. Used to give government department "secretaries" oversight of their
-- department account without spending rights.
--
-- Mirrors account_members / account_group_members (direct UUID + LuckPerms
-- group, soft-deleted via left_at). Unlike authorizers, a viewer is standalone
-- — it does NOT need to be a member first, so there is no composite FK to the
-- member tables.
-- =====================================================================
CREATE TABLE account_viewers (
    account_id        INT UNSIGNED NOT NULL,
    viewer_uuid_bin   BINARY(16)   NOT NULL,
    added_by_uuid_bin BINARY(16)   NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at           TIMESTAMP    NULL,
    PRIMARY KEY (account_id, viewer_uuid_bin),
    KEY idx_viewer_uuid (viewer_uuid_bin),
    KEY idx_viewer_active (account_id, left_at),
    CONSTRAINT fk_viewer_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_group_viewers (
    account_id        INT UNSIGNED  NOT NULL,
    lp_group          VARCHAR(64)   NOT NULL,
    added_by_uuid_bin BINARY(16)    NOT NULL,
    created_at        TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    left_at           TIMESTAMP(3)  NULL,
    PRIMARY KEY (account_id, lp_group),
    KEY idx_agv_active (account_id, left_at),
    CONSTRAINT fk_agv_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
