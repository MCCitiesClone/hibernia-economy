import 'server-only';
import { sql } from 'kysely';
import { db, binToUuid } from '@/lib/db';

export interface AdminApiKey {
  key_id: number;
  key_type: string;
  owner_uuid: string | null;
  owner_name: string | null;
  account_id: number | null;
  account_name: string | null;
  firm_id: number | null;
  firm_name: string | null;
  revoked: number;
  issued_at: Date | null;
  expires_at: Date | null;
}

/** Mirrors ApiKeyMapper.listForAdmin (line 35-44). */
export async function listAdminApiKeys(): Promise<AdminApiKey[]> {
  const r = await sql<{
    key_id: number;
    key_type: string;
    owner_uuid_bin: Buffer | null;
    owner_name: string | null;
    account_id: number | null;
    account_name: string | null;
    firm_id: number | null;
    firm_name: string | null;
    revoked: number;
    issued_at: Date | null;
    expires_at: Date | null;
  }>`
    SELECT k.key_id, k.key_type, k.owner_uuid_bin, fp.current_name AS owner_name,
           k.account_id, a.display_name AS account_name,
           k.firm_id, f.display_name AS firm_name,
           k.revoked, k.issued_at, k.expires_at
    FROM api_keys k
    LEFT JOIN firm_players fp ON fp.player_uuid_bin = k.owner_uuid_bin
    LEFT JOIN accounts a ON a.account_id = k.account_id
    LEFT JOIN firm f ON f.firm_id = k.firm_id
    ORDER BY k.revoked ASC, k.issued_at DESC
  `.execute(db);
  return r.rows.map((row) => ({
    key_id: row.key_id,
    key_type: row.key_type,
    owner_uuid: row.owner_uuid_bin ? binToUuid(row.owner_uuid_bin) : null,
    owner_name: row.owner_name,
    account_id: row.account_id,
    account_name: row.account_name,
    firm_id: row.firm_id,
    firm_name: row.firm_name,
    revoked: row.revoked,
    issued_at: row.issued_at,
    expires_at: row.expires_at,
  }));
}

/** Mirrors ApiKeyMapper.setRevoked (line 47-48). */
export async function setApiKeyRevoked(keyId: number): Promise<void> {
  await sql`UPDATE api_keys SET revoked = 1 WHERE key_id = ${keyId}`.execute(db);
}

export interface ApiKeyRotateRow {
  key_id: number;
  key_type: string;
  owner_uuid: string | null;
  account_id: number | null;
  firm_id: number | null;
  revoked: number;
}

/** Fetches the columns the rotate flow needs to mint a fresh JWT. */
export async function findApiKeyForRotate(keyId: number): Promise<ApiKeyRotateRow | null> {
  const r = await sql<{
    key_id: number;
    key_type: string;
    owner_uuid_bin: Buffer | null;
    account_id: number | null;
    firm_id: number | null;
    revoked: number;
  }>`
    SELECT key_id, key_type, owner_uuid_bin, account_id, firm_id, revoked
    FROM api_keys WHERE key_id = ${keyId}
  `.execute(db);
  const row = r.rows[0];
  if (!row) return null;
  return {
    key_id: row.key_id,
    key_type: row.key_type,
    owner_uuid: row.owner_uuid_bin ? binToUuid(row.owner_uuid_bin) : null,
    account_id: row.account_id,
    firm_id: row.firm_id,
    revoked: row.revoked,
  };
}

/** Mirrors ApiKeyMapper.rotateKey (line 60-68). Returns rows-affected. */
export async function rotateApiKeyRow(args: {
  keyId: number;
  jti: string;
  token: string;
  issuedAt: Date;
  expiresAt: Date;
}): Promise<number> {
  const r = await sql`
    UPDATE api_keys
    SET jwt_id = ${args.jti}, token = ${args.token},
        issued_at = ${args.issuedAt}, expires_at = ${args.expiresAt}
    WHERE key_id = ${args.keyId} AND revoked = 0
  `.execute(db);
  return Number(r.numAffectedRows ?? 0);
}
