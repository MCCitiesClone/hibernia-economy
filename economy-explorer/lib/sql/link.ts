import 'server-only';
import { sql } from 'kysely';
import { db } from '@/lib/db';

/**
 * Mirrors treasury-rest-api's ExplorerLinkCodeMapper.insert.
 * Expiry computed DB-side (`NOW() + ttl`) so it always matches the redeem check.
 */
export async function insertLinkCode(args: {
  code: string;
  sub: string;
  minecraftName: string | null;
  ttlSeconds: number;
}): Promise<void> {
  await sql`
    INSERT INTO explorer_link_code (code, keycloak_sub, minecraft_name, expires_at)
    VALUES (${args.code}, ${args.sub}, ${args.minecraftName}, NOW() + INTERVAL ${args.ttlSeconds} SECOND)
  `.execute(db);
}

/** Drops expired codes; called opportunistically before each insert. */
export async function deleteExpiredLinkCodes(): Promise<void> {
  await sql`DELETE FROM explorer_link_code WHERE expires_at < NOW()`.execute(db);
}
