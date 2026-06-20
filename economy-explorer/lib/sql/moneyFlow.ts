import 'server-only';
import { sql } from 'kysely';
import { db } from '@/lib/db';

export interface MoneyFlowEdge {
  from_type: string;
  to_type: string;
  amount: string;
  /** `amount` minus starting-balance seed transfers ("Initial player funds").
   * Used by the flow diagram so per-player account seeding — a huge, one-off
   * GOVERNMENT→PERSONAL flow — doesn't crush every organic edge to a hairline.
   * The table/KPIs still use the full `amount`. */
  amount_ex_seed: string;
  txn_count: number;
}

/** Mirrors LedgerExplorerMapper.getMoneyFlow (line 416-430). */
export async function getMoneyFlow(days: number): Promise<MoneyFlowEdge[]> {
  const r = await sql<{ fromType: string; toType: string; amount: string; amountExSeed: string; txnCount: string | number }>`
    SELECT a_src.account_type AS fromType,
           a_dst.account_type AS toType,
           SUM(d.amt) AS amount,
           -- Seed transfers carry this exact message (LedgerServiceImpl
           -- .resolveOrCreatePersonal → "Initial player funds"). Excluded from
           -- the diagram's magnitude so account seeding doesn't dwarf real flow.
           SUM(CASE WHEN lt.message = 'Initial player funds' THEN 0 ELSE d.amt END) AS amountExSeed,
           COUNT(*) AS txnCount
    FROM (
      -- One streaming pass over the window's postings: pivot each txn's two legs
      -- (debit = src, credit = dst) and keep only simple 2-posting transfers (a
      -- 3+-posting txn is ambiguous to attribute). Windowed by txn_id (monotonic
      -- with creation) so GROUP BY txn_id reads the (account_id,txn_id)/txn index
      -- in order and STREAMS — the old settlement_time window forced a filesort of
      -- ~890k groups, which pushed the query past the 15s statement timeout so it
      -- never populated the cache. The exact settlement window is re-applied below
      -- on the survivors; the txn_id cutoff is a guaranteed superset, so the result
      -- is identical.
      SELECT lp.txn_id,
             MAX(CASE WHEN lp.amount < 0 THEN lp.account_id END) AS src_acct,
             MAX(CASE WHEN lp.amount > 0 THEN lp.account_id END) AS dst_acct,
             MAX(CASE WHEN lp.amount > 0 THEN lp.amount END) AS amt,
             COUNT(*) AS n
      FROM ledger_postings lp
      WHERE lp.txn_id >= (SELECT MIN(txn_id) FROM ledger_txns WHERE settlement_time >= NOW() - INTERVAL ${days} DAY)
      GROUP BY lp.txn_id
      HAVING n = 2 AND src_acct IS NOT NULL AND dst_acct IS NOT NULL
    ) d
    JOIN ledger_txns lt ON lt.txn_id = d.txn_id
    JOIN accounts a_src ON a_src.account_id = d.src_acct
    JOIN accounts a_dst ON a_dst.account_id = d.dst_acct
    WHERE a_src.account_type != a_dst.account_type
      AND lt.settlement_time >= NOW() - INTERVAL ${days} DAY
    GROUP BY a_src.account_type, a_dst.account_type
    HAVING SUM(d.amt) > 0
    ORDER BY amount DESC
  `.execute(db);
  return r.rows.map((row) => ({
    from_type: row.fromType,
    to_type: row.toType,
    amount: row.amount,
    amount_ex_seed: row.amountExSeed,
    txn_count: Number(row.txnCount),
  }));
}
