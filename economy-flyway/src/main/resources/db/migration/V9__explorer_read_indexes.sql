-- =====================================================================
-- V9 — Read-path indexes for the economy-explorer SSR app.
--
-- economy-explorer (Next.js, replaces the treasury-rest-api explorer UI)
-- renders pages by reading this DB directly through Kysely. Its queries
-- live in `lib/sql/*.ts`, each a 1:1 port of a Spring @Select. A pass over
-- all of them found a handful that scan or filesort a *continuously growing*
-- table with no supporting index. Each index below names the explorer
-- function(s) it serves and why the current plan hurts.
--
-- Every statement is a pure additive ADD INDEX on a write-rarely column, so
-- all are InnoDB online DDL (ALGORITHM=INPLACE, LOCK=NONE) — safe to apply
-- against the populated prod tables without blocking writers.
--
-- Tables NOT touched here, deliberately:
--   * chestshop_sale / chestshop_shop — V6 already shipped the full set of
--     (filter, occurred_at) / (item_key|material, active) indexes the market
--     pages need. Re-verified against lib/sql/market.ts; nothing missing.
--   * firm / firm_* — covered by V1 (idx_firm_account_active, idx_role_active,
--     idx_emp_current/player, uq_firm_display_name, …) and V8
--     (idx_firm_proprietor). The explorer's firm reads hit those directly.
--   * account_balances_mat.balance — STILL deliberately unindexed, same
--     reasoning as V7: the balance trigger rewrites it on every posting, so
--     indexing it would add write amplification to the transfer hot path. The
--     explorer's balance-sorted views (getTopAccounts, listAccounts sort=balance,
--     getGovernmentAccounts) therefore filesort a small, already-filtered set —
--     an accepted trade vs. taxing every transfer. Revisit only if those views
--     dominate and a read-replica/cache can absorb the sort instead.
--
-- Searches NOT indexable here: the explorer's text filters
-- (listAccounts q=, listTransactions q=, listFirms q=, market search=,
-- listAudit actor=, searchGlobal) all use `LIKE CONCAT('%', ?, '%')` —
-- leading-wildcard, so no B-tree can serve them. Speeding those up means
-- FULLTEXT/trigram + MATCH-AGAINST query rewrites, which is a schema *and*
-- query-shape change, out of scope for an indexing pass. Flagged for a future
-- migration if substring search becomes a bottleneck.
-- =====================================================================

-- 1. ledger_txns(plugin_system, settlement_time)
--
-- ledger.ts listTransactions / countTransactions power the global transactions
-- page, which has a plugin_system facet. With the facet applied the query is
--   WHERE lt.plugin_system = ? ORDER BY lt.settlement_time DESC
-- but the only indexes on ledger_txns are idx_ledger_settle_time
-- (settlement_time alone) and the dedup/initiator/authorizer keys — none lead
-- with plugin_system. So a plugin filter scans the whole (largest, fastest-
-- growing) table in the DB and filesorts it. Leading on plugin_system cuts to
-- that source's rows, and the trailing settlement_time satisfies the ORDER BY
-- via a backward range scan (no filesort). The unfiltered list is unaffected —
-- it keeps using idx_ledger_settle_time.
ALTER TABLE ledger_txns
    ADD INDEX idx_ledger_plugin_time (plugin_system, settlement_time);

-- 2. government_fines(issued_at)
--
-- government.ts getRecentFines does
--   WHERE gf.issued_at >= NOW() - INTERVAL ? DAY ORDER BY gf.issued_at DESC LIMIT ?
-- and getFineCategorySummary filters the same window. government_fines only
-- carries idx_fines_player (player_uuid_bin) + the PK, so both queries scan
-- every fine ever issued and filesort. This index turns the window into a
-- range seek and serves the ORDER BY directly. issued_at is write-once.
ALTER TABLE government_fines
    ADD INDEX idx_fines_issued_at (issued_at);

-- 3. player_login_times(last_login_epoch)
--
-- health.ts getActivePlayersSummary runs three COUNT(*) subqueries per health
-- page load:
--   SELECT COUNT(*) FROM player_login_times WHERE last_login_epoch >= UNIX_TIMESTAMP() - {1,7,30}*86400
-- The table is PK(player_uuid_bin) only, so each is a full scan — one row per
-- player, growing with the playerbase, scanned 3× every render. An index on
-- last_login_epoch makes each a range count.
ALTER TABLE player_login_times
    ADD INDEX idx_login_last_epoch (last_login_epoch);

-- 4. firm_players(first_seen)
--
-- health.ts getNewPlayersDaily does
--   WHERE first_seen >= NOW() - INTERVAL ? DAY GROUP BY DATE(first_seen)
-- firm_players is PK(player_uuid_bin) + uq(name_lower) only, so this scans the
-- entire player directory (one row per player ever seen) to find a recent
-- window. Indexing first_seen range-seeks the window before grouping.
-- (Note: this does NOT help the `current_name LIKE '%q%'` player searches —
-- those are leading-wildcard, see header.)
ALTER TABLE firm_players
    ADD INDEX idx_firm_players_first_seen (first_seen);

-- 5. explorer_audit(at)
--
-- audit.ts listAudit is the access-log firehose; its default (unfiltered) view
-- is ORDER BY at DESC, audit_id DESC LIMIT/OFFSET. explorer_audit only has
-- idx_audit_actor (actor_uuid_bin, at) and idx_audit_target (target_type,
-- target_id, at) — neither leads with `at`, so the default view filesorts the
-- whole, append-only, ever-growing audit table on every page. A plain (at)
-- index serves the ORDER BY via a backward scan. (The target_type-filtered
-- admin view still seeks idx_audit_target; its subset is small enough that the
-- residual sort is cheap, so no second composite is added here.)
ALTER TABLE explorer_audit
    ADD INDEX idx_audit_at (at);

-- 6. accounts(created_at)
--
-- ledger.ts listAccounts exposes a sort=created option ->
--   ORDER BY a.created_at <dir>, a.account_id DESC
-- accounts is indexed on owner_uuid_bin, account_type, and (account_type,
-- display_name) (V7) — nothing on created_at — so choosing that sort filesorts
-- the full filtered account set. created_at is set once at insert and never
-- updated, so the write cost is a single leaf insert; it also backs any
-- "newest accounts" view cheaply.
ALTER TABLE accounts
    ADD INDEX idx_accounts_created (created_at);
