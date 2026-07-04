-- ============================================================================
-- RE-RUNNABLE backfill: align every active firm's corporate account(s) with the
-- firm's current proprietor.
--
-- Fixes the "changed proprietor can't withdraw — you're not an authorizer"
-- stranding caused by BOTH un-deployed code paths:
--   * player proprietorship transfer  (PAR-141)
--   * /firm admin set proprietor       (PAR-315)
--
-- firm.proprietor_uuid_bin is the source of truth for ownership, so the invariant
-- restored is: for every active firm account, the Treasury owner + an active
-- member + an active authorizer all equal the firm's current proprietor, and the
-- previous owner's stale access is dropped (unless they're a current employee).
--
-- IDEMPOTENT and SAFE TO RE-RUN. Until PAR-141/PAR-315 are deployed, new
-- transfers/admin changes keep stranding accounts, so run this on a schedule
-- (e.g. hourly cron:  mysql --defaults-file=... economy < backfill-firm-account-owners.sql )
-- and drop the schedule once the fix ships.
--
-- Scope note: this INCLUDES the 3 admin-path firms (GoldmanCapitalBank 3113,
-- InnerBanking 3122, ElytraPayments 3144 — proprietor Planke32). To hold those
-- back, add:  AND f.firm_id NOT IN (3113,3122,3144)  to the INSERT ... SELECT below.
-- ============================================================================

-- The step-1 UPDATE scopes rows via a JOIN to the temp table rather than a WHERE,
-- which trips MySQL safe-update mode (and GUI "UPDATE without WHERE" guards).
-- Disable it for this session (resets when the connection closes); a WHERE is
-- also added below so clients that lint the text don't warn either.
SET SQL_SAFE_UPDATES = 0;

START TRANSACTION;

-- Snapshot the accounts to fix BEFORE any mutation changes the owner<>proprietor
-- predicate (so steps 2-4 act on the same set step 1 reassigns).
CREATE TEMPORARY TABLE _fix (
    account_id INT UNSIGNED NOT NULL PRIMARY KEY,
    firm_id    INT          NOT NULL,
    new_owner  BINARY(16)   NOT NULL
);

INSERT INTO _fix (account_id, firm_id, new_owner)
SELECT fa.account_id, f.firm_id, f.proprietor_uuid_bin
FROM firm f
JOIN firm_accounts fa ON fa.firm_id = f.firm_id AND fa.removed_at IS NULL
JOIN accounts a       ON a.account_id = fa.account_id AND a.is_archived = 0
WHERE f.is_archived = 0
  AND a.owner_uuid_bin <> f.proprietor_uuid_bin;

-- (1) Owner -> current proprietor. The JOIN to _fix already scopes this to the
--     accounts being fixed; the WHERE is redundant but silences UPDATE-without-WHERE
--     guards and skips no-op rows.
UPDATE accounts a JOIN _fix x ON x.account_id = a.account_id
SET a.owner_uuid_bin = x.new_owner
WHERE a.owner_uuid_bin <> x.new_owner;

-- (2) Proprietor is an active member (reactivate a left row, else insert).
INSERT INTO account_members (account_id, member_uuid_bin, added_by_uuid_bin)
SELECT x.account_id, x.new_owner, x.new_owner FROM _fix x
ON DUPLICATE KEY UPDATE left_at = NULL;

-- (3) Proprietor is an active authorizer (reactivate a revoked row, else insert).
INSERT INTO account_authorizers (account_id, authorizer_uuid_bin, added_by_uuid_bin)
SELECT x.account_id, x.new_owner, x.new_owner FROM _fix x
ON DUPLICATE KEY UPDATE revoked_at = NULL;

-- (4) Revoke stale access on the fixed accounts: anyone who is neither the new
--     proprietor nor a current employee (drops the previous owner). This mirrors
--     the plugin's reconcile. Delete this block to leave prior access untouched.
UPDATE account_authorizers auth JOIN _fix x ON x.account_id = auth.account_id
SET auth.revoked_at = CURRENT_TIMESTAMP
WHERE auth.revoked_at IS NULL
  AND auth.authorizer_uuid_bin <> x.new_owner
  AND NOT EXISTS (SELECT 1 FROM firm_employee fe
                   WHERE fe.firm_id = x.firm_id
                     AND fe.player_uuid_bin = auth.authorizer_uuid_bin
                     AND fe.is_current = 1);

UPDATE account_members mem JOIN _fix x ON x.account_id = mem.account_id
SET mem.left_at = CURRENT_TIMESTAMP
WHERE mem.left_at IS NULL
  AND mem.member_uuid_bin <> x.new_owner
  AND NOT EXISTS (SELECT 1 FROM firm_employee fe
                   WHERE fe.firm_id = x.firm_id
                     AND fe.player_uuid_bin = mem.member_uuid_bin
                     AND fe.is_current = 1);

DROP TEMPORARY TABLE _fix;

COMMIT;
