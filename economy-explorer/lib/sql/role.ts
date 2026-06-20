import 'server-only';
import { db, uuidToBin } from '@/lib/db';

export type ExplorerRole = 'admin' | 'government';

/**
 * Elevated roles ('admin' | 'government') granted to a player.
 * Mirrors treasury-rest-api's ExplorerRoleMapper.findRoles.
 * Returns an empty array for plain players — baseline 'player' is implicit
 * and not stored in this table.
 */
export async function findRoles(playerUuid: string): Promise<ExplorerRole[]> {
  const rows = await db
    .selectFrom('explorer_role')
    .select('role')
    .where('player_uuid_bin', '=', uuidToBin(playerUuid))
    .execute();
  return rows.map((r) => r.role).filter(isExplorerRole);
}

function isExplorerRole(r: string): r is ExplorerRole {
  return r === 'admin' || r === 'government';
}
