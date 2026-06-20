import 'server-only';
import { sql } from 'kysely';
import { db, binToUuid, uuidToBin } from '@/lib/db';

export interface RateLimitOverride {
  owner_uuid: string;
  multiplier: string;
  note: string | null;
  updated_at: Date;
}

/** Mirrors ApiRateLimitOverrideMapper.findAll. */
export async function listRateLimitOverrides(): Promise<RateLimitOverride[]> {
  const r = await sql<{
    owner_uuid: Buffer;
    multiplier: string;
    note: string | null;
    updated_at: Date;
  }>`
    SELECT owner_uuid_bin AS owner_uuid, multiplier, note, updated_at
    FROM api_rate_limit_override
  `.execute(db);
  return r.rows.map((row) => ({
    owner_uuid: binToUuid(row.owner_uuid),
    multiplier: row.multiplier,
    note: row.note,
    updated_at: row.updated_at,
  }));
}

/** Mirrors ApiRateLimitOverrideMapper.upsert (line 25-32). */
export async function upsertRateLimitOverride(args: {
  ownerUuid: string;
  multiplier: string;
  note: string | null;
  updatedBy: string | null;
}): Promise<void> {
  await sql`
    INSERT INTO api_rate_limit_override (owner_uuid_bin, multiplier, note, updated_by_bin)
    VALUES (
      ${uuidToBin(args.ownerUuid)},
      ${args.multiplier},
      ${args.note},
      ${args.updatedBy ? uuidToBin(args.updatedBy) : null}
    )
    ON DUPLICATE KEY UPDATE
      multiplier = VALUES(multiplier),
      note = VALUES(note),
      updated_by_bin = VALUES(updated_by_bin)
  `.execute(db);
}

/** Mirrors ApiRateLimitOverrideMapper.delete (line 34-35). */
export async function deleteRateLimitOverride(ownerUuid: string): Promise<void> {
  await sql`DELETE FROM api_rate_limit_override WHERE owner_uuid_bin = ${uuidToBin(ownerUuid)}`.execute(db);
}
