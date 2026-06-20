import 'server-only';
import { db } from '@/lib/db';
import {
  listGroups as listGroupsDal,
  selectGroupById,
  createGroup as createGroupRow,
  deleteGroup as deleteGroupRow,
  deleteGroupCapabilities,
  insertGroupCapability,
  setLuckpermsNode as setLuckpermsNodeRow,
  listGroupMembers as listGroupMembersDal,
  addManualMember as addManualMemberRow,
  removeManualMember as removeManualMemberRow,
  findPlayerUuidByName,
  type GroupSummary,
  type GroupMember,
} from '@/lib/sql/group';

/**
 * Group RBAC service. Owns the orchestration and validation for the group admin
 * tools — the multi-statement capability replacement and the admin identifier
 * resolution — and exposes the rest of the domain as thin pass-throughs over the
 * pure {@code lib/sql/group} DAL, so pages and server actions depend on this
 * service rather than reaching the DAL directly.
 */

export type { GroupSummary, GroupMember };

const UUID_RE =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export function listGroups(): Promise<GroupSummary[]> {
  return listGroupsDal();
}

export function getGroup(groupId: number): Promise<GroupSummary | null> {
  return selectGroupById(groupId);
}

export function createGroup(args: {
  name: string;
  description: string | null;
  createdBy: string | null;
}): Promise<void> {
  return createGroupRow(args);
}

export function deleteGroup(groupId: number): Promise<void> {
  return deleteGroupRow(groupId);
}

export function setLuckpermsNode(groupId: number, node: string | null): Promise<void> {
  return setLuckpermsNodeRow(groupId, node);
}

export function listGroupMembers(groupId: number): Promise<GroupMember[]> {
  return listGroupMembersDal(groupId);
}

export function addManualMember(
  groupId: number,
  playerUuid: string,
  addedBy: string | null,
): Promise<void> {
  return addManualMemberRow(groupId, playerUuid, addedBy);
}

export function removeManualMember(groupId: number, playerUuid: string): Promise<void> {
  return removeManualMemberRow(groupId, playerUuid);
}

/**
 * Replaces a group's capability set wholesale, atomically: the DELETE and the
 * per-capability INSERTs run in a single transaction so a failure can't leave a
 * group with a partial set. The transaction boundary is the service's concern;
 * each statement is a pure DAL call.
 */
export async function setGroupCapabilities(
  groupId: number,
  capabilities: string[],
): Promise<void> {
  await db.transaction().execute(async (trx) => {
    await deleteGroupCapabilities(groupId, trx);
    for (const cap of capabilities) {
      await insertGroupCapability(groupId, cap, trx);
    }
  });
}

/**
 * Resolves an admin-typed member identifier to a UUID: a canonical UUID is
 * accepted (and lower-cased) verbatim; anything else is looked up by current
 * name. Returns null if unresolved.
 */
export async function resolvePlayerUuid(input: string): Promise<string | null> {
  const trimmed = input.trim();
  if (UUID_RE.test(trimmed)) {
    return trimmed.toLowerCase();
  }
  return findPlayerUuidByName(trimmed);
}
