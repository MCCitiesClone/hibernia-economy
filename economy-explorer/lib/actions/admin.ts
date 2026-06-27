'use server';
import { revalidatePath } from 'next/cache';
import { randomUUID } from 'node:crypto';
import { getViewer, type Viewer } from '@/lib/auth/viewer';
import {
  setApiKeyRevoked,
  findApiKeyForRotate,
  rotateApiKeyRow,
} from '@/lib/sql/apiKey';
import { setRateLimitOverride, clearRateLimitOverride } from '@/lib/treasury';
import { signApiKeyJwt, jwtSigningConfigured } from '@/lib/jwt';
import { ForbiddenError } from '@/lib/errors';
import { auditView } from '@/lib/audit';
import {
  createGroup,
  deleteGroup,
  setGroupCapabilities,
  setLuckpermsNode,
  addManualMember,
  removeManualMember,
  resolvePlayerUuid,
} from '@/lib/services/group';
import { isCapability } from '@/lib/auth/capabilities';

const TOKEN_LIFETIME_DAYS = 180;

function requireAdmin(viewer: Viewer): asserts viewer is Extract<Viewer, { anon: false }> {
  if (viewer.anon || viewer.role !== 'admin') {
    throw new ForbiddenError('Admin only.');
  }
}

// ---- Group RBAC management -------------------------------------------------

type ActionResult = { ok: boolean; error?: string };

export async function createGroupAction(args: { name: string; description: string | null }): Promise<ActionResult> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    const name = args.name.trim();
    if (!name) return { ok: false, error: 'Name is required.' };
    await createGroup({ name, description: args.description?.trim() || null, createdBy: viewer.minecraftUuid });
    await auditView(viewer, { method: 'POST', path: '/admin/groups/create', targetType: 'global', targetId: `group:${name}` });
    revalidatePath('/admin/groups');
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function deleteGroupAction(groupId: number): Promise<ActionResult> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    await deleteGroup(groupId);
    await auditView(viewer, { method: 'POST', path: '/admin/groups/delete', targetType: 'global', targetId: `group:${groupId}` });
    revalidatePath('/admin/groups');
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function setGroupCapabilitiesAction(groupId: number, capabilities: string[]): Promise<ActionResult> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    const valid = capabilities.filter(isCapability);
    await setGroupCapabilities(groupId, valid);
    await auditView(viewer, { method: 'POST', path: '/admin/groups/capabilities', targetType: 'global', targetId: `group:${groupId}` });
    revalidatePath(`/admin/groups/${groupId}`);
    revalidatePath('/admin/groups');
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function setLuckpermsNodeAction(groupId: number, node: string | null): Promise<ActionResult> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    await setLuckpermsNode(groupId, node?.trim() || null);
    await auditView(viewer, { method: 'POST', path: '/admin/groups/luckperms-node', targetType: 'global', targetId: `group:${groupId}` });
    revalidatePath(`/admin/groups/${groupId}`);
    revalidatePath('/admin/groups');
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function addGroupMemberAction(groupId: number, identifier: string): Promise<ActionResult> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    const uuid = await resolvePlayerUuid(identifier);
    if (!uuid) return { ok: false, error: `No player found for "${identifier}".` };
    await addManualMember(groupId, uuid, viewer.minecraftUuid);
    await auditView(viewer, { method: 'POST', path: '/admin/groups/member-add', targetType: 'player', targetId: uuid });
    revalidatePath(`/admin/groups/${groupId}`);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function removeGroupMemberAction(groupId: number, playerUuid: string): Promise<ActionResult> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    await removeManualMember(groupId, playerUuid);
    await auditView(viewer, { method: 'POST', path: '/admin/groups/member-remove', targetType: 'player', targetId: playerUuid });
    revalidatePath(`/admin/groups/${groupId}`);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function revokeApiKeyAction(keyId: number): Promise<{ ok: boolean; error?: string }> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    await setApiKeyRevoked(keyId);
    await auditView(viewer, { method: 'POST', path: '/admin/api-keys/revoke', targetType: 'global', targetId: `key:${keyId}` });
    revalidatePath('/admin/api-keys');
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/**
 * Admin force-rotate: mints a new JWT, invalidates the old token, returns
 * only the new expiry (never the token — same security choice as Spring's
 * adminForceRotate). The owner must re-export the new token in-game.
 */
export async function rotateApiKeyAction(keyId: number): Promise<{ ok: boolean; expiresAt?: string; error?: string }> {
  const viewer = await getViewer();
  requireAdmin(viewer);

  if (!jwtSigningConfigured()) {
    return { ok: false, error: 'JWT signing is not configured in this environment (missing JWT_SECRET).' };
  }

  try {
    const row = await findApiKeyForRotate(keyId);
    if (!row) return { ok: false, error: 'API key not found.' };
    if (row.revoked) return { ok: false, error: 'Key is revoked; revoked keys cannot be rotated.' };
    if (!row.owner_uuid) return { ok: false, error: 'Key has no owner UUID — cannot rotate.' };

    const jti = randomUUID();
    const iat = new Date();
    const exp = new Date(iat.getTime() + TOKEN_LIFETIME_DAYS * 24 * 60 * 60 * 1000);

    const token = await signApiKeyJwt({
      keyId: row.key_id,
      ownerUuid: row.owner_uuid,
      keyType: row.key_type,
      accountId: row.account_id,
      firmId: row.firm_id,
      jti,
      iat,
      exp,
    });

    const affected = await rotateApiKeyRow({ keyId, jti, token, issuedAt: iat, expiresAt: exp });
    if (affected !== 1) {
      return { ok: false, error: 'Rotation failed (key revoked concurrently).' };
    }
    await auditView(viewer, { method: 'POST', path: '/admin/api-keys/rotate', targetType: 'global', targetId: `key:${keyId}` });
    revalidatePath('/admin/api-keys');
    return { ok: true, expiresAt: exp.toISOString() };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/**
 * Set or clear a per-issuer rate-limit multiplier. multiplier = 1 with empty
 * note clears the row (returns to default); otherwise upserts. Clamped to
 * 0.10..1000, mirroring AdminApiKeyController.setRateLimit.
 */
export async function setRateLimitAction(args: {
  ownerUuid: string;
  multiplier: number;
  note: string | null;
}): Promise<{ ok: boolean; error?: string }> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    const mult = clamp(args.multiplier, 0.1, 1000);
    // Route the write through the REST admin API (ADT-14) — kysely stays read-only.
    if (mult === 1 && (!args.note || !args.note.trim())) {
      await clearRateLimitOverride(args.ownerUuid);
    } else {
      await setRateLimitOverride(args.ownerUuid, mult.toFixed(2), args.note?.trim() || null);
    }
    await auditView(viewer, { method: 'POST', path: '/admin/api-keys/rate-limit', targetType: 'player', targetId: args.ownerUuid });
    revalidatePath('/admin/api-keys');
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}
