-- Composite index for account lookups by type + display name.
--
-- findGovernmentAccountByName / findSystemAccountForPlugin / findBusinessAccountByName
-- all filter `account_type = ? AND display_name = ?`, but only idx_accounts_type
-- (account_type) existed — so they scanned every row of that type to match the
-- name. Gov/system sets are tiny, but BUSINESS accounts grow one-per-firm, so that
-- lookup degraded into a type-wide scan. This index turns all three into a direct
-- seek. display_name changes rarely, so the write cost is negligible.
--
-- Deliberately NOT indexing account_balances_mat.balance (which would speed
-- /baltop's ORDER BY balance DESC): that column is rewritten by the balance
-- trigger on every posting, so indexing it would add write amplification to the
-- hottest path (transfers). The leaderboard is better served by an in-plugin cache.
ALTER TABLE accounts
    ADD INDEX idx_accounts_type_name (account_type, display_name);
