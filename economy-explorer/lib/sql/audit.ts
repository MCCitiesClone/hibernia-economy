import 'server-only';
import { sql } from 'kysely';
import { db, binToUuid } from '@/lib/db';

export interface AuditEntry {
  audit_id: number;
  at: Date;
  actor_sub: string;
  actor_uuid: string | null;
  actor_name: string | null;
  actor_role: string | null;
  method: string | null;
  path: string | null;
  target_type: string | null;
  target_id: string | null;
  outcome: number;
  source_ip: string | null;
}

/** Mirrors ExplorerAuditMapper.list (line 30-44). */
export async function listAudit(args: {
  actor: string | null;
  targetType: string | null;
  limit: number;
  offset: number;
}): Promise<AuditEntry[]> {
  const conds: ReturnType<typeof sql>[] = [];
  if (args.actor) conds.push(sql`actor_name LIKE CONCAT('%', ${args.actor}, '%')`);
  if (args.targetType) conds.push(sql`target_type = ${args.targetType}`);
  const where = conds.length ? sql`WHERE ${sql.join(conds, sql` AND `)}` : sql``;

  const r = await sql<{
    audit_id: number;
    at: Date;
    actor_sub: string;
    actor_uuid_bin: Buffer | null;
    actor_name: string | null;
    actor_role: string | null;
    method: string | null;
    path: string | null;
    target_type: string | null;
    target_id: string | null;
    outcome: number;
    source_ip: string | null;
  }>`
    SELECT audit_id, at, actor_sub, actor_uuid_bin, actor_name, actor_role, method, path,
           target_type, target_id, outcome, source_ip
    FROM explorer_audit
    ${where}
    ORDER BY at DESC, audit_id DESC
    LIMIT ${args.limit} OFFSET ${args.offset}
  `.execute(db);
  return r.rows.map((row) => ({
    audit_id: row.audit_id,
    at: row.at,
    actor_sub: row.actor_sub,
    actor_uuid: row.actor_uuid_bin ? binToUuid(row.actor_uuid_bin) : null,
    actor_name: row.actor_name,
    actor_role: row.actor_role,
    method: row.method,
    path: row.path,
    target_type: row.target_type,
    target_id: row.target_id,
    outcome: row.outcome,
    source_ip: row.source_ip,
  }));
}

export async function countAudit(args: { actor: string | null; targetType: string | null }): Promise<number> {
  const conds: ReturnType<typeof sql>[] = [];
  if (args.actor) conds.push(sql`actor_name LIKE CONCAT('%', ${args.actor}, '%')`);
  if (args.targetType) conds.push(sql`target_type = ${args.targetType}`);
  const where = conds.length ? sql`WHERE ${sql.join(conds, sql` AND `)}` : sql``;
  const r = await sql<{ c: string | number }>`SELECT COUNT(*) AS c FROM explorer_audit ${where}`.execute(db);
  return Number(r.rows[0]?.c ?? 0);
}
