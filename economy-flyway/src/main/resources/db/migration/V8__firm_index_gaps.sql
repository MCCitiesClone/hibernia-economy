-- Three missing indexes + one missing UNIQUE on the firm tables, all flagged by
-- the 2026-05-26 schema-quality review of business-rian. Each is a pure addition
-- (no column changes, no data rewrites) and safe to apply against the populated
-- prod tables — with one caveat called out at #4 below.
--
-- Deliberately NOT included in this migration:
--   * firm.discord_url format CHECK (`LIKE 'https://%'`). Adding a CHECK on an
--     existing column with non-conforming rows would block the migration; needs
--     a one-shot audit/cleanup of `firm.discord_url` values first.
--   * firm_role "at most one is_default per (firm, non-deleted)" partial-unique.
--     The new schema's idiom is a virtual column + UNIQUE that treats NULLs as
--     distinct, but adding it would fail if existing data already has multiple
--     defaults for the same firm. Needs a dedup pass first. Also worth landing
--     a corresponding service-level invariant in FirmRoleServiceImpl at the
--     same time — currently the column is set but nothing reads or guards it.
--   * firm.last_proprietor_changed_at. Derivable from firm_transfer_requests
--     (most recent ACCEPTED row); denormalisation for query convenience, not a
--     correctness gap.

-- 1. firm(proprietor_uuid_bin)
--
-- The firm table is filtered on proprietor_uuid_bin by six distinct mapper
-- queries — FirmMapper.findFirmByProprietor / listFirmsByProprietor /
-- existsFirmOwnedBy / countOwnedByProprietor / get*ForProprietor and
-- FirmAdminMapper.countOwnedFirms. Without this index every one of those
-- queries scans the firm table. The index is one BINARY(16) per row, and
-- writes against proprietor_uuid_bin only happen on createFirm and on
-- proprietorship transfer — both rare relative to the read pattern.
ALTER TABLE firm
    ADD INDEX idx_firm_proprietor (proprietor_uuid_bin);

-- 2. firm_invites(status, expires_at)
--
-- ExpireRequestsJob.expireStaleInvites runs every 30 minutes and executes:
--   UPDATE firm_invites
--      SET status = 'EXPIRED'
--    WHERE status NOT IN ('ACCEPTED','DENIED','EXPIRED')
--      AND expires_at <= NOW()
-- The only existing index on this table starts with firm_id (the
-- partial-unique uq_one_pending_invite), so the job currently scans every row
-- of firm_invites ever created. Leading on `status` cuts the candidate set to
-- the small open subset (PENDING is the only NOT-IN-the-terminal-set status
-- the job will see in practice), and the index then range-seeks expires_at
-- within that.
ALTER TABLE firm_invites
    ADD INDEX idx_invites_status_expiry (status, expires_at);

-- 3. firm_transfer_requests(status, expires_at)
--
-- Same shape as #2, for ExpireRequestsJob.expireStaleTransfers. A far smaller
-- table than firm_invites today, but the access pattern is identical and the
-- per-tick cost should not depend on table size.
ALTER TABLE firm_transfer_requests
    ADD INDEX idx_transfers_status_expiry (status, expires_at);

-- 4. UNIQUE firm_sale(source_msg_id)
--
-- source_msg_id was added in V1 with the intent of de-duplicating re-fired
-- source events (the column allows NULL because not every recording path
-- carries a message id). The UNIQUE constraint was never added, so the
-- declared intent has never actually been enforced — a re-fired ChestShop
-- listener event today will silently insert the same sale twice.
--
-- MariaDB treats NULLs as distinct in UNIQUE indexes, so legacy rows where
-- source_msg_id IS NULL remain valid; only non-NULL duplicates are now
-- rejected.
--
-- ⚠️ If existing data already contains non-NULL duplicate source_msg_id
-- values, this ALTER will fail. That outcome is the right one: the
-- duplicates need to be deduped manually (or the offending rows deleted)
-- before re-applying. Sketch query to find them first if you want to check:
--   SELECT source_msg_id, COUNT(*) FROM firm_sale
--    WHERE source_msg_id IS NOT NULL
--    GROUP BY source_msg_id HAVING COUNT(*) > 1;
ALTER TABLE firm_sale
    ADD CONSTRAINT uq_sale_source_msg_id UNIQUE (source_msg_id);
