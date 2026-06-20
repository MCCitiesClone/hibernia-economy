-- Transaction-feed webhooks for the Treasury REST API.
--
-- Player-built finance infra (banks, funds) registers HTTPS endpoints via the
-- REST API and the always-on API tails the ledger (ledger_txns, monotonic
-- txn_id) and POSTs new, *settled* transactions to them — HMAC-signed, retried,
-- at-least-once. This is the push counterpart to the cursor "pull" feed
-- (GET /accounts/{id}/transactions?since=<txn_id>). Substrate for players to
-- build on; the network does not provide the banking gameplay itself.
--
-- Three tables:
--   * webhook_subscription — one row per registered endpoint, scoped to the API
--     key that created it (api_keys.key_id). PERSONAL/GOVERNMENT keys are scoped
--     to their account_id; BUSINESS keys to their firm_id (all the firm's
--     accounts). Deleting/revoking the key cascades the subscription away.
--   * webhook_delivery — durable outbox / retry queue. One row per
--     (subscription, txn); UNIQUE makes enqueue idempotent so a re-scanned txn
--     is never double-delivered. The dispatcher backs off via next_attempt_at.
--   * webhook_cursor — single-row global tail watermark. Seeded to the current
--     MAX(txn_id) at install so the dispatcher delivers from deploy *forward*
--     (history is the pull endpoint's job, not a replay storm).

CREATE TABLE webhook_subscription (
    subscription_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    api_key_id           INT UNSIGNED    NOT NULL,
    owner_uuid_bin       BINARY(16)      NOT NULL COMMENT 'snapshot of the issuing key''s owner (api_keys.owner_uuid_bin)',
    key_type             ENUM('PERSONAL','BUSINESS','GOVERNMENT') NOT NULL,
    account_id           INT UNSIGNED    NULL COMMENT 'scope for PERSONAL/GOVERNMENT keys',
    firm_id              INT             NULL COMMENT 'scope for BUSINESS keys',
    target_url           VARCHAR(2048)   NOT NULL,
    secret               CHAR(64)        NOT NULL COMMENT 'per-subscription HMAC-SHA256 signing key (hex)',
    active               TINYINT(1)      NOT NULL DEFAULT 1,
    consecutive_failures INT UNSIGNED    NOT NULL DEFAULT 0,
    disabled_at          TIMESTAMP       NULL COMMENT 'set when auto-disabled after repeated delivery failures',
    created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (subscription_id),
    KEY idx_websub_key (api_key_id),
    KEY idx_websub_owner (owner_uuid_bin),
    KEY idx_websub_active_account (active, account_id),
    KEY idx_websub_active_firm (active, firm_id),
    CONSTRAINT fk_websub_api_key FOREIGN KEY (api_key_id)
        REFERENCES api_keys(key_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE webhook_delivery (
    delivery_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT UNSIGNED NOT NULL,
    txn_id          BIGINT UNSIGNED NOT NULL,
    account_id      INT UNSIGNED    NOT NULL COMMENT 'the subscribed account the txn matched (payload scope)',
    status          ENUM('PENDING','DELIVERED','FAILED') NOT NULL DEFAULT 'PENDING',
    attempts        INT UNSIGNED    NOT NULL DEFAULT 0,
    http_status     INT             NULL,
    last_error      VARCHAR(255)    NULL,
    next_attempt_at TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (delivery_id),
    UNIQUE KEY uq_delivery_sub_txn (subscription_id, txn_id),
    KEY idx_delivery_due (status, next_attempt_at),
    CONSTRAINT fk_delivery_sub FOREIGN KEY (subscription_id)
        REFERENCES webhook_subscription(subscription_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE webhook_cursor (
    id                     TINYINT UNSIGNED NOT NULL DEFAULT 1,
    last_dispatched_txn_id BIGINT UNSIGNED  NOT NULL DEFAULT 0,
    updated_at             TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed the watermark to "now" so the dispatcher starts delivering from deploy
-- forward instead of replaying the entire ledger backlog on first poll.
INSERT INTO webhook_cursor (id, last_dispatched_txn_id)
SELECT 1, COALESCE(MAX(txn_id), 0) FROM ledger_txns;
