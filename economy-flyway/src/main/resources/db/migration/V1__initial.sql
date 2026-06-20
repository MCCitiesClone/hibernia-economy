-- =====================================================================
-- V1 — Initial schema for the shared DemocracyCraft economy database.
--
-- Combines (single greenfield migration; no V2 follow-on):
--   - Treasury core (accounts, ledger, members, authorizers, group access,
--     government_fines, player_login_times, balance triggers, UUID helpers)
--   - Business (firm and friends, FKs into Treasury's accounts)
--   - api_keys in its post-V001 shape (key_type, firm_id, FK to firm)
--   - Cross-project feature_flags table with the explorer privacy gate
--
-- Soft-delete policy: nothing in this database is hard-deleted. Tables
-- that need a delete-shaped operation (fines revoked, accounts archived,
-- employees terminated) carry an explicit flag/timestamp column instead.
-- All FKs into accounts/ledger_txns therefore use ON DELETE RESTRICT, so
-- any accidental hard-delete attempt fails loudly rather than silently
-- cascading or orphaning audit data.
--
-- Targets MariaDB 10.6+ (virtual columns, JSON funcs, triggers, FK).
-- Assumes a fresh database. To bring an existing pre-Flyway DB under
-- management, run `flywayMigrate -Pbaseline=true` so Flyway records a
-- baseline row at version 0 instead of trying to apply V1 against a
-- populated schema.
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =====================================================================
-- UUID helper functions
--
-- No `DELIMITER //` directives — those are a MySQL CLI artefact, not SQL.
-- Flyway's MariaDB parser recognises stored-program bodies natively.
-- =====================================================================
CREATE FUNCTION uuid_to_bin(_uuid CHAR(36))
    RETURNS BINARY(16) DETERMINISTIC
BEGIN
    RETURN UNHEX(REPLACE(_uuid, '-', ''));
END;

CREATE FUNCTION bin_to_uuid(_bin BINARY(16))
    RETURNS CHAR(36) DETERMINISTIC
BEGIN
    RETURN INSERT(INSERT(INSERT(INSERT(HEX(_bin), 9, 0, '-'), 14, 0, '-'), 19, 0, '-'), 24, 0, '-');
END;

-- =====================================================================
-- ACCOUNTS  (Treasury)
-- =====================================================================
CREATE TABLE accounts (
    account_id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_type            ENUM('PERSONAL','BUSINESS','GOVERNMENT','SYSTEM') NOT NULL,
    owner_uuid_bin          BINARY(16)   NOT NULL,
    display_name            VARCHAR(255),
    requires_authorization  TINYINT(1)   NOT NULL DEFAULT 0,
    is_archived             TINYINT(1)   NOT NULL DEFAULT 0,
    allow_overdraft         TINYINT(1)   NOT NULL DEFAULT 0,
    credit_limit            DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Virtual column: non-NULL only for PERSONAL rows. Enables a unique
    -- constraint that prevents duplicate PERSONAL accounts per player
    -- without affecting other types.
    _personal_owner         BINARY(16) AS (
        CASE WHEN account_type = 'PERSONAL' THEN owner_uuid_bin END
    ) VIRTUAL,
    PRIMARY KEY (account_id),
    UNIQUE KEY uq_one_personal_per_player (_personal_owner),
    KEY idx_accounts_owner (owner_uuid_bin),
    KEY idx_accounts_type (account_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Materialised balances. Maintained by triggers on ledger_postings.
CREATE TABLE account_balances_mat (
    account_id INT UNSIGNED  NOT NULL,
    balance    DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    version    BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id),
    CONSTRAINT fk_abm_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- MEMBERS & AUTHORIZERS  (Treasury)
--
-- Soft-deleted: left_at / revoked_at flag the row as inactive instead of
-- removing it. Re-adding a previously-removed member uses INSERT … ON
-- DUPLICATE KEY UPDATE to clear the flag (see MembershipMapper). All
-- "is this player a member?" reads filter on `… IS NULL`.
-- =====================================================================
CREATE TABLE account_members (
    account_id        INT UNSIGNED NOT NULL,
    member_uuid_bin   BINARY(16)   NOT NULL,
    added_by_uuid_bin BINARY(16)   NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at           TIMESTAMP    NULL,
    PRIMARY KEY (account_id, member_uuid_bin),
    KEY idx_member_uuid (member_uuid_bin),
    KEY idx_member_active (account_id, left_at),
    CONSTRAINT fk_member_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- An authorizer must also be a member (composite FK).
CREATE TABLE account_authorizers (
    account_id          INT UNSIGNED NOT NULL,
    authorizer_uuid_bin BINARY(16)   NOT NULL,
    added_by_uuid_bin   BINARY(16)   NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at          TIMESTAMP    NULL,
    PRIMARY KEY (account_id, authorizer_uuid_bin),
    KEY idx_auth_uuid (authorizer_uuid_bin),
    KEY idx_auth_active (account_id, revoked_at),
    CONSTRAINT fk_auth_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_auth_is_member FOREIGN KEY (account_id, authorizer_uuid_bin)
        REFERENCES account_members(account_id, member_uuid_bin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- LEDGER  (Treasury)
-- =====================================================================
CREATE TABLE ledger_txns (
    txn_id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trade_time           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settlement_time      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message              VARCHAR(255)    NOT NULL,
    initiator_uuid_bin   BINARY(16)      NOT NULL,
    authorizer_uuid_bin  BINARY(16)      NULL,
    plugin_system        VARCHAR(64)     NULL,
    client_dedup_key     BINARY(32)      NULL,
    PRIMARY KEY (txn_id),
    UNIQUE KEY uq_ledger_dedup (client_dedup_key),
    KEY idx_ledger_settle_time (settlement_time),
    KEY idx_ledger_initiator (initiator_uuid_bin),
    KEY idx_ledger_authorizer (authorizer_uuid_bin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ledger_postings (
    posting_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    txn_id     BIGINT UNSIGNED NOT NULL,
    account_id INT UNSIGNED    NOT NULL,
    amount     DECIMAL(19,2)   NOT NULL,
    memo       VARCHAR(255)    NULL,
    PRIMARY KEY (posting_id),
    KEY idx_postings_txn (txn_id),
    KEY idx_postings_account (account_id),
    KEY idx_postings_account_txn_amount (account_id, txn_id, amount),
    CONSTRAINT fk_post_txn FOREIGN KEY (txn_id)
        REFERENCES ledger_txns(txn_id) ON DELETE CASCADE,
    CONSTRAINT fk_post_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- LP GROUP MEMBERSHIP  (Treasury — LuckPerms group access)
-- =====================================================================
CREATE TABLE account_group_members (
    account_id        INT UNSIGNED  NOT NULL,
    lp_group          VARCHAR(64)   NOT NULL,
    added_by_uuid_bin BINARY(16)    NOT NULL,
    created_at        TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    left_at           TIMESTAMP(3)  NULL,
    PRIMARY KEY (account_id, lp_group),
    KEY idx_agm_active (account_id, left_at),
    CONSTRAINT fk_agm_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_group_authorizers (
    account_id        INT UNSIGNED  NOT NULL,
    lp_group          VARCHAR(64)   NOT NULL,
    added_by_uuid_bin BINARY(16)    NOT NULL,
    created_at        TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revoked_at        TIMESTAMP(3)  NULL,
    PRIMARY KEY (account_id, lp_group),
    KEY idx_aga_active (account_id, revoked_at),
    CONSTRAINT fk_aga_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_aga_group_member FOREIGN KEY (account_id, lp_group)
        REFERENCES account_group_members(account_id, lp_group) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- GOVERNMENT FINES  (Treasury)
-- =====================================================================
CREATE TABLE government_fines (
    fine_id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    player_uuid_bin     BINARY(16)      NOT NULL,
    gov_account_id      INT UNSIGNED    NOT NULL,
    amount              DECIMAL(19,2)   NOT NULL,
    reason              VARCHAR(255)    NOT NULL,
    txn_id              BIGINT UNSIGNED NOT NULL,
    issued_by_uuid_bin  BINARY(16)      NOT NULL,
    issued_at           TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revoked             TINYINT(1)      NOT NULL DEFAULT 0,
    revoked_by_uuid_bin BINARY(16)      NULL,
    revoke_txn_id       BIGINT UNSIGNED NULL,
    revoked_at          TIMESTAMP(3)    NULL,
    PRIMARY KEY (fine_id),
    KEY idx_fines_player (player_uuid_bin),
    CONSTRAINT fk_fines_gov_account FOREIGN KEY (gov_account_id)
        REFERENCES accounts(account_id)    ON DELETE RESTRICT,
    CONSTRAINT fk_fines_txn         FOREIGN KEY (txn_id)
        REFERENCES ledger_txns(txn_id)     ON DELETE RESTRICT,
    CONSTRAINT fk_fines_revoke_txn  FOREIGN KEY (revoke_txn_id)
        REFERENCES ledger_txns(txn_id)     ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- PLAYER LOGIN TIMES  (Treasury — for prorated balance tax)
-- =====================================================================
CREATE TABLE player_login_times (
    player_uuid_bin   BINARY(16) NOT NULL,
    last_login_epoch  BIGINT     NOT NULL,
    PRIMARY KEY (player_uuid_bin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- BUSINESS — firm and friends
-- (FKs into Treasury's `accounts` are why Business can't migrate
-- independently — both sets of tables live together in one DB.)
-- =====================================================================
CREATE TABLE firm_players (
    player_uuid_bin BINARY(16)   NOT NULL,
    current_name    VARCHAR(16)  NOT NULL,
    name_lower      VARCHAR(16)  AS (LOWER(current_name)) VIRTUAL,
    first_seen      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_firm_players PRIMARY KEY (player_uuid_bin),
    CONSTRAINT uq_firm_players_name UNIQUE (name_lower)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE firm (
    firm_id             INT          NOT NULL AUTO_INCREMENT,
    display_name        VARCHAR(255) NOT NULL,
    proprietor_uuid_bin BINARY(16)   NOT NULL,
    discord_url         VARCHAR(255) NULL,
    hq_region           VARCHAR(64)  NULL,
    default_account_id  INT UNSIGNED NULL,
    is_archived         TINYINT(1)   NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (firm_id),
    UNIQUE KEY uq_firm_display_name (display_name),
    UNIQUE KEY uq_firm_default_account_id (default_account_id),
    CONSTRAINT fk_firm_default_account FOREIGN KEY (default_account_id)
        REFERENCES accounts(account_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE firm_accounts (
    firm_id    INT          NOT NULL,
    account_id INT UNSIGNED NOT NULL,
    added_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at TIMESTAMP    NULL,
    PRIMARY KEY (firm_id, account_id),
    UNIQUE KEY uq_account_once (account_id),
    KEY idx_firm_account_active (firm_id, removed_at),
    CONSTRAINT fk_firm_account_firm FOREIGN KEY (firm_id)
        REFERENCES firm(firm_id) ON DELETE CASCADE,
    CONSTRAINT fk_firm_account_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE firm_role (
    role_id            INT         NOT NULL AUTO_INCREMENT,
    firm_id            INT         NOT NULL,
    name               VARCHAR(64) NOT NULL,
    rank_order         INT         NOT NULL,
    is_proprietor_like TINYINT(1)  NOT NULL DEFAULT 0,
    is_default         TINYINT(1)  NOT NULL DEFAULT 0,
    deleted_at         TIMESTAMP   NULL,
    PRIMARY KEY (role_id),
    UNIQUE KEY uq_role_name (firm_id, name),
    UNIQUE KEY uq_role_rank (firm_id, rank_order),
    KEY idx_role_active (firm_id, deleted_at),
    CONSTRAINT fk_role_firm FOREIGN KEY (firm_id)
        REFERENCES firm(firm_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE firm_role_permission (
    role_id    INT NOT NULL,
    permission ENUM('ADMIN','FINANCIAL','CHESTSHOP','DEFAULT') NOT NULL,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (role_id, permission),
    KEY idx_rp_active (role_id, deleted_at),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id)
        REFERENCES firm_role(role_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE firm_employee (
    firm_id             INT         NOT NULL,
    player_uuid_bin     BINARY(16)  NOT NULL,
    role_id             INT         NOT NULL,
    joined_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at             TIMESTAMP   NULL,
    added_by_uuid_bin   BINARY(16)  NULL,
    removed_by_uuid_bin BINARY(16)  NULL,
    -- Enforce history + "one current job per firm/player" via a virtual NULL trick.
    is_current TINYINT(1) GENERATED ALWAYS AS (left_at IS NULL) VIRTUAL,
    PRIMARY KEY (firm_id, player_uuid_bin, joined_at),
    KEY idx_emp_current (firm_id, is_current, role_id),
    KEY idx_emp_player  (player_uuid_bin, is_current),
    CONSTRAINT fk_emp_firm FOREIGN KEY (firm_id)
        REFERENCES firm(firm_id) ON DELETE CASCADE,
    CONSTRAINT fk_emp_role FOREIGN KEY (role_id)
        REFERENCES firm_role(role_id),
    -- Exactly one active employment per player per firm
    CONSTRAINT uq_emp_one_current UNIQUE (firm_id, player_uuid_bin, is_current),
    -- If they've left, we know who removed them
    CHECK (
        (left_at IS NULL AND removed_by_uuid_bin IS NULL)
        OR
        (left_at IS NOT NULL AND removed_by_uuid_bin IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE firm_invites (
    invite_id           BIGINT      NOT NULL AUTO_INCREMENT,
    firm_id             INT         NOT NULL,
    player_uuid_bin     BINARY(16)  NOT NULL,
    invited_by_uuid_bin BINARY(16)  NOT NULL,
    expires_at          DATETIME    NOT NULL,
    status              ENUM('PENDING','ACCEPTED','DENIED','EXPIRED')
                                    NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Only one pending invite per firm/player
    pending_only TINYINT(1) GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END) VIRTUAL,
    PRIMARY KEY (invite_id),
    CONSTRAINT fk_inv_firm FOREIGN KEY (firm_id)
        REFERENCES firm(firm_id) ON DELETE CASCADE,
    CONSTRAINT uq_one_pending_invite UNIQUE (firm_id, player_uuid_bin, pending_only)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE firm_transfer_requests (
    transfer_id   BIGINT     NOT NULL AUTO_INCREMENT,
    firm_id       INT        NOT NULL,
    from_uuid_bin BINARY(16) NOT NULL,
    to_uuid_bin   BINARY(16) NOT NULL,
    token         CHAR(64)   NOT NULL,
    expires_at    DATETIME   NOT NULL,
    status        ENUM('PENDING','CONFIRMED','ACCEPTED','CANCELLED','EXPIRED')
                             NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Only one active transfer per firm (PENDING/CONFIRMED)
    active_only TINYINT(1) GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING','CONFIRMED') THEN 1 ELSE NULL END) VIRTUAL,
    PRIMARY KEY (transfer_id),
    UNIQUE KEY uq_token (token),
    CONSTRAINT uq_one_active_transfer UNIQUE (firm_id, active_only),
    CONSTRAINT fk_tr_firm FOREIGN KEY (firm_id)
        REFERENCES firm(firm_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE firm_properties (
    firm_id    INT          NOT NULL,
    `key`      VARCHAR(128) NOT NULL,
    value      TEXT         NOT NULL,
    type       ENUM('STRING','INTEGER','BIGDECIMAL','BOOLEAN') NOT NULL,
    deleted_at TIMESTAMP    NULL,
    PRIMARY KEY (firm_id, `key`),
    KEY idx_fp_active (firm_id, deleted_at),
    CONSTRAINT fk_fp_firm FOREIGN KEY (firm_id)
        REFERENCES firm(firm_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE firm_sale (
    sale_id        BIGINT       NOT NULL AUTO_INCREMENT,
    firm_id        INT          NOT NULL,
    occurred_at    DATETIME     NOT NULL,
    buyer_uuid_bin BINARY(16)   NULL,
    world          VARCHAR(32)  NOT NULL,
    x              INT          NOT NULL,
    y              INT          NOT NULL,
    z              INT          NOT NULL,
    item_id        VARCHAR(64)  NOT NULL,
    item_name      VARCHAR(128) NOT NULL,
    qty            INT          NOT NULL,
    price          DECIMAL(19,4) NOT NULL,
    source_msg_id  VARCHAR(128) NULL,
    PRIMARY KEY (sale_id),
    KEY idx_sale_firm_time (firm_id, occurred_at),
    CONSTRAINT fk_sale_firm FOREIGN KEY (firm_id)
        REFERENCES firm(firm_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- API KEYS  (treasury-api-plugin issues, treasury-rest-api verifies)
-- This is the post-V001 shape: typed (PERSONAL/BUSINESS/GOVERNMENT),
-- with firm_id for BUSINESS keys (account_id is null in that case).
-- =====================================================================
CREATE TABLE api_keys (
    key_id         INT UNSIGNED NOT NULL AUTO_INCREMENT,
    key_type       ENUM('PERSONAL','BUSINESS','GOVERNMENT') NOT NULL DEFAULT 'PERSONAL',
    account_id     INT UNSIGNED NULL,
    firm_id        INT          NULL,
    owner_uuid_bin BINARY(16)   NOT NULL,
    jwt_id         CHAR(36)     NOT NULL COMMENT 'jti claim — updated on reissue',
    token          TEXT         NOT NULL COMMENT 'Full signed JWT',
    issued_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at     TIMESTAMP    NOT NULL,
    revoked        TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (key_id),
    UNIQUE KEY uq_api_key_jwt_id (jwt_id),
    KEY idx_api_key_account (account_id),
    KEY idx_api_key_owner   (owner_uuid_bin),
    KEY idx_api_key_type    (key_type),
    KEY idx_api_key_firm    (firm_id),
    CONSTRAINT fk_api_key_account FOREIGN KEY (account_id)
        REFERENCES accounts(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_api_key_firm FOREIGN KEY (firm_id)
        REFERENCES firm(firm_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- VIEW
-- =====================================================================
CREATE OR REPLACE VIEW account_balances AS
SELECT a.account_id, COALESCE(abm.balance, 0.00) AS balance
FROM accounts a
LEFT JOIN account_balances_mat abm ON abm.account_id = a.account_id;

-- =====================================================================
-- TRIGGERS — maintain account_balances_mat from ledger_postings.
--
-- Why DB-side and not Java-side: multiple writers exist (Treasury plugin,
-- the REST API in `treasury-rest-api/`) and a Java applyDelta would race.
-- Letting MariaDB own the balance update keeps `account_balances_mat`
-- consistent under any combination of writers.
-- =====================================================================
CREATE TRIGGER trg_postings_ai
    AFTER INSERT ON ledger_postings
    FOR EACH ROW
BEGIN
    INSERT INTO account_balances_mat (account_id, balance, version)
    VALUES (NEW.account_id, NEW.amount, 1)
    ON DUPLICATE KEY UPDATE
        balance = balance + NEW.amount,
        version = version + 1;
END;

CREATE TRIGGER trg_postings_au
    AFTER UPDATE ON ledger_postings
    FOR EACH ROW
BEGIN
    IF OLD.account_id = NEW.account_id THEN
        UPDATE account_balances_mat
        SET balance = balance + (NEW.amount - OLD.amount),
            version = version + 1
        WHERE account_id = NEW.account_id;
    ELSE
        -- Posting moved between accounts: reverse old, apply new.
        UPDATE account_balances_mat
        SET balance = balance - OLD.amount,
            version = version + 1
        WHERE account_id = OLD.account_id;

        INSERT INTO account_balances_mat (account_id, balance, version)
        VALUES (NEW.account_id, NEW.amount, 1)
        ON DUPLICATE KEY UPDATE
            balance = balance + NEW.amount,
            version = version + 1;
    END IF;
END;

CREATE TRIGGER trg_postings_ad
    AFTER DELETE ON ledger_postings
    FOR EACH ROW
BEGIN
    UPDATE account_balances_mat
    SET balance = balance - OLD.amount,
        version = version + 1
    WHERE account_id = OLD.account_id;
END;

-- =====================================================================
-- FEATURE FLAGS  (cross-project)
--
-- Shared, runtime-toggleable kill-switch surface for features across
-- Treasury, Business, treasury-api-plugin, and treasury-rest-api. Flag
-- names use a dotted namespace ("subsystem.feature_name") so each project
-- can claim a prefix without colliding (e.g. "explorer.*", "treasury.*").
--
-- Code that reads a flag should treat a missing row as "disabled" so
-- introducing a new flag is safe — additions ship as INSERTs in future
-- migrations or via a runtime admin tool.
-- =====================================================================
CREATE TABLE feature_flags (
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512) NOT NULL,
    enabled      TINYINT(1)   NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO feature_flags (name, description, enabled) VALUES
    ('explorer.player_history_visible',
     'Public ledger explorer: surface per-player and per-account transaction history (account transactions, counterparties, the global transactions list, individual transaction detail, the ChestShop trade list). When disabled, the explorer only shows aggregate stats and account balances — matching what other players can already see in-game via /balance and /baltop.',
     0);
