import 'server-only';
import { sql } from 'kysely';
import { db, uuidToBin, binToUuid } from '@/lib/db';

/**
 * Explorer group RBAC queries. A player's effective capabilities are the union
 * of every group they belong to (see findCapabilities); the rest is admin-tool
 * CRUD for managing groups, their capabilities, their LuckPerms source node, and
 * manual membership. LuckPerms-sourced membership is owned by the reconciliation
 * cron (treasury-api-plugin) and is read-only here.
 */

export interface GroupSummary {
  groupId: number;
  name: string;
  description: string | null;
  luckpermsNode: string | null;
  capabilities: string[];
  memberCount: number;
  luckpermsMemberCount: number;
}

export interface GroupMember {
  uuid: string;
  name: string | null;
  source: 'manual' | 'luckperms';
  addedAt: Date;
}

/** The capabilities a player has via group membership. */
export async function findCapabilities(playerUuid: string): Promise<string[]> {
  const r = await sql<{ capability: string }>`
    SELECT DISTINCT gc.capability
    FROM explorer_group_member gm
    JOIN explorer_group_capability gc ON gc.group_id = gm.group_id
    WHERE gm.player_uuid_bin = ${uuidToBin(playerUuid)}
  `.execute(db);
  return r.rows.map((row) => row.capability);
}

export async function listGroups(): Promise<GroupSummary[]> {
  const r = await sql<{
    group_id: number;
    name: string;
    description: string | null;
    luckperms_node: string | null;
    capabilities: string | null;
    member_count: number | string;
    luckperms_count: number | string;
  }>`
    SELECT g.group_id, g.name, g.description, g.luckperms_node,
           (SELECT GROUP_CONCAT(gc.capability ORDER BY gc.capability)
              FROM explorer_group_capability gc WHERE gc.group_id = g.group_id) AS capabilities,
           (SELECT COUNT(*) FROM explorer_group_member gm WHERE gm.group_id = g.group_id) AS member_count,
           (SELECT COUNT(*) FROM explorer_group_member gm
              WHERE gm.group_id = g.group_id AND gm.source = 'luckperms') AS luckperms_count
    FROM explorer_group g
    ORDER BY g.name
  `.execute(db);
  return r.rows.map((row) => ({
    groupId: row.group_id,
    name: row.name,
    description: row.description,
    luckpermsNode: row.luckperms_node,
    capabilities: row.capabilities ? row.capabilities.split(',') : [],
    memberCount: Number(row.member_count),
    luckpermsMemberCount: Number(row.luckperms_count),
  }));
}

export async function getGroup(groupId: number): Promise<GroupSummary | null> {
  const groups = await listGroups();
  return groups.find((g) => g.groupId === groupId) ?? null;
}

/**
 * Resolve an admin-typed member identifier to a UUID: accepts a canonical UUID
 * verbatim, otherwise looks up the most-recent player by current name (case-
 * insensitive). Returns null if unresolved.
 */
export async function resolvePlayerUuid(input: string): Promise<string | null> {
  const trimmed = input.trim();
  if (/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(trimmed)) {
    return trimmed.toLowerCase();
  }
  const r = await sql<{ player_uuid_bin: Buffer }>`
    SELECT player_uuid_bin FROM firm_players
    WHERE LOWER(current_name) = LOWER(${trimmed})
    ORDER BY player_uuid_bin LIMIT 1
  `.execute(db);
  return r.rows[0] ? binToUuid(r.rows[0].player_uuid_bin) : null;
}

export async function createGroup(args: {
  name: string;
  description: string | null;
  createdBy: string | null;
}): Promise<void> {
  await sql`
    INSERT INTO explorer_group (name, description, created_by_uuid_bin)
    VALUES (${args.name}, ${args.description}, ${args.createdBy ? uuidToBin(args.createdBy) : null})
  `.execute(db);
}

export async function deleteGroup(groupId: number): Promise<void> {
  // capabilities + members cascade via FK ON DELETE CASCADE.
  await sql`DELETE FROM explorer_group WHERE group_id = ${groupId}`.execute(db);
}

/** Replace a group's capability set wholesale. */
export async function setGroupCapabilities(groupId: number, capabilities: string[]): Promise<void> {
  await db.transaction().execute(async (trx) => {
    await sql`DELETE FROM explorer_group_capability WHERE group_id = ${groupId}`.execute(trx);
    for (const cap of capabilities) {
      await sql`
        INSERT INTO explorer_group_capability (group_id, capability) VALUES (${groupId}, ${cap})
      `.execute(trx);
    }
  });
}

export async function setLuckpermsNode(groupId: number, node: string | null): Promise<void> {
  await sql`UPDATE explorer_group SET luckperms_node = ${node} WHERE group_id = ${groupId}`.execute(db);
}

export async function listGroupMembers(groupId: number): Promise<GroupMember[]> {
  const r = await sql<{
    player_uuid_bin: Buffer;
    name: string | null;
    source: 'manual' | 'luckperms';
    added_at: Date;
  }>`
    SELECT gm.player_uuid_bin, fp.current_name AS name, gm.source, gm.added_at
    FROM explorer_group_member gm
    LEFT JOIN firm_players fp ON fp.player_uuid_bin = gm.player_uuid_bin
    WHERE gm.group_id = ${groupId}
    ORDER BY gm.source, fp.current_name
  `.execute(db);
  return r.rows.map((row) => ({
    uuid: binToUuid(row.player_uuid_bin),
    name: row.name,
    source: row.source,
    addedAt: row.added_at,
  }));
}

export async function addManualMember(groupId: number, playerUuid: string, addedBy: string | null): Promise<void> {
  await sql`
    INSERT IGNORE INTO explorer_group_member (group_id, player_uuid_bin, source, added_by_uuid_bin)
    VALUES (${groupId}, ${uuidToBin(playerUuid)}, 'manual', ${addedBy ? uuidToBin(addedBy) : null})
  `.execute(db);
}

/** Removes a manual grant only — LuckPerms-sourced rows are owned by the cron. */
export async function removeManualMember(groupId: number, playerUuid: string): Promise<void> {
  await sql`
    DELETE FROM explorer_group_member
    WHERE group_id = ${groupId} AND player_uuid_bin = ${uuidToBin(playerUuid)} AND source = 'manual'
  `.execute(db);
}
