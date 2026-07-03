-- ============================================================================
-- Backfill: repair firm corporate accounts stranded by the firm-transfer
-- account-reassignment regression (transfers ACCEPTED from 2026-06-12 onward,
-- when the deployed build stopped calling reassignAccountsToNewProprietor).
--
-- Symptom fixed: the new proprietor gets "you're not an authorizer" and cannot
-- withdraw, because after the transfer the account owner/authorizer/member were
-- left on the PREVIOUS proprietor.
--
-- Scope: 13 firms (14 accounts) with an unambiguous ACCEPTED transfer.
-- EXCLUDES the 3 Planke32 no-transfer firms (3113 GoldmanCapitalBank,
-- 3122 InnerBanking, 3144 ElytraPayments) — those are a different, creation-time
-- anomaly and need a human to confirm the intended owner first.
--
-- Run inside the transaction; run the VERIFY query before COMMIT.
-- ============================================================================

START TRANSACTION;

-- (1) Reassign each firm account's owner to the firm's CURRENT proprietor.
UPDATE accounts a
  JOIN firm_accounts fa ON fa.account_id = a.account_id AND fa.removed_at IS NULL
  JOIN firm f           ON f.firm_id = fa.firm_id
SET a.owner_uuid_bin = f.proprietor_uuid_bin
WHERE f.firm_id IN (2312,2344,2375,2388,2397,2445,2448,2638,2643,2702,2777,3136,3423)
  AND a.is_archived = 0
  AND a.owner_uuid_bin <> f.proprietor_uuid_bin;

-- (2) Ensure the current proprietor is an ACTIVE member of every firm account.
INSERT INTO account_members (account_id, member_uuid_bin, added_by_uuid_bin)
SELECT a.account_id, f.proprietor_uuid_bin, f.proprietor_uuid_bin
FROM firm f
  JOIN firm_accounts fa ON fa.firm_id = f.firm_id AND fa.removed_at IS NULL
  JOIN accounts a       ON a.account_id = fa.account_id AND a.is_archived = 0
WHERE f.firm_id IN (2312,2344,2375,2388,2397,2445,2448,2638,2643,2702,2777,3136,3423)
ON DUPLICATE KEY UPDATE left_at = NULL;

-- (3) Ensure the current proprietor is an ACTIVE authorizer of every firm account.
INSERT INTO account_authorizers (account_id, authorizer_uuid_bin, added_by_uuid_bin)
SELECT a.account_id, f.proprietor_uuid_bin, f.proprietor_uuid_bin
FROM firm f
  JOIN firm_accounts fa ON fa.firm_id = f.firm_id AND fa.removed_at IS NULL
  JOIN accounts a       ON a.account_id = fa.account_id AND a.is_archived = 0
WHERE f.firm_id IN (2312,2344,2375,2388,2397,2445,2448,2638,2643,2702,2777,3136,3423)
ON DUPLICATE KEY UPDATE revoked_at = NULL;

-- (4) OPTIONAL — revoke the previous proprietor's stale access. Strips only
--     someone who is neither the new proprietor nor a current employee, so the
--     two firms where the old owner stayed on as staff (AllyPharmacy2/unalign,
--     UAGCapital/Brzzzes) are left untouched. Delete this block to keep old owners.
UPDATE account_authorizers auth
  JOIN firm_accounts fa ON fa.account_id = auth.account_id AND fa.removed_at IS NULL
  JOIN firm f           ON f.firm_id = fa.firm_id
SET auth.revoked_at = CURRENT_TIMESTAMP
WHERE f.firm_id IN (2312,2344,2375,2388,2397,2445,2448,2638,2643,2702,2777,3136,3423)
  AND auth.revoked_at IS NULL
  AND auth.authorizer_uuid_bin <> f.proprietor_uuid_bin
  AND NOT EXISTS (SELECT 1 FROM firm_employee fe
                   WHERE fe.firm_id = f.firm_id
                     AND fe.player_uuid_bin = auth.authorizer_uuid_bin
                     AND fe.is_current = 1);

UPDATE account_members mem
  JOIN firm_accounts fa ON fa.account_id = mem.account_id AND fa.removed_at IS NULL
  JOIN firm f           ON f.firm_id = fa.firm_id
SET mem.left_at = CURRENT_TIMESTAMP
WHERE f.firm_id IN (2312,2344,2375,2388,2397,2445,2448,2638,2643,2702,2777,3136,3423)
  AND mem.left_at IS NULL
  AND mem.member_uuid_bin <> f.proprietor_uuid_bin
  AND NOT EXISTS (SELECT 1 FROM firm_employee fe
                   WHERE fe.firm_id = f.firm_id
                     AND fe.player_uuid_bin = mem.member_uuid_bin
                     AND fe.is_current = 1);

-- ---- VERIFY (run before COMMIT): every row must be owner_mismatch=0, prop_missing_auth=0 ----
SELECT f.firm_id, f.display_name,
       SUM(a.owner_uuid_bin <> f.proprietor_uuid_bin)                       AS owner_mismatch,
       SUM(auth.account_id IS NULL)                                          AS prop_missing_auth
FROM firm f
JOIN firm_accounts fa ON fa.firm_id = f.firm_id AND fa.removed_at IS NULL
JOIN accounts a       ON a.account_id = fa.account_id AND a.is_archived = 0
LEFT JOIN account_authorizers auth
       ON auth.account_id = a.account_id
      AND auth.authorizer_uuid_bin = f.proprietor_uuid_bin
      AND auth.revoked_at IS NULL
WHERE f.firm_id IN (2312,2344,2375,2388,2397,2445,2448,2638,2643,2702,2777,3136,3423)
GROUP BY f.firm_id, f.display_name;

-- COMMIT;    -- uncomment & run once VERIFY shows all zeros. Otherwise: ROLLBACK;
