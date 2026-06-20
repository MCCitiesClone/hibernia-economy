'use server';
import { revalidatePath } from 'next/cache';
import { randomBytes } from 'node:crypto';
import { getViewer, type Viewer } from '@/lib/auth/viewer';
import { ForbiddenError } from '@/lib/errors';
import { auditView } from '@/lib/audit';
import { assertPublicHttpsUrl } from '@/lib/util/ssrf';
import { findAccountsForPlayer } from '@/lib/sql/me';
import { hasFirmFinancialAccess } from '@/lib/sql/firm';
import { findAccount } from '@/lib/sql/ledger';
import {
  createSubscription,
  setActive,
  setUrl,
  rotateSecret,
  deleteSubscription,
  adminCreateForAccount,
  adminSetActive,
  adminDelete,
} from '@/lib/sql/webhook';

export type WebhookActionResult = { ok: boolean; error?: string; secret?: string };

/** The signing secret is returned once on create/rotate; the caller shows it then discards it. */
function newSecret(): string {
  return randomBytes(32).toString('hex');
}

/** Asserts a signed-in, account-linked viewer and returns their Minecraft UUID. */
function requireLinkedUuid(viewer: Viewer): string {
  if (viewer.anon || !viewer.linked || !viewer.minecraftUuid) {
    throw new ForbiddenError('Sign in and link your Minecraft account to manage webhooks.');
  }
  return viewer.minecraftUuid;
}

/**
 * Create a webhook scoped to the player's own personal account or a firm they
 * have FINANCIAL/ADMIN access to. Scope is re-derived/authorised server-side —
 * never trust a client-supplied account id.
 */
export async function createWebhookAction(args: {
  scope: 'account' | 'firm';
  firmId?: number;
  url: string;
}): Promise<WebhookActionResult> {
  const viewer = await getViewer();
  const uuid = requireLinkedUuid(viewer);
  try {
    await assertPublicHttpsUrl(args.url);
    const url = args.url.trim();

    let keyType: 'PERSONAL' | 'BUSINESS';
    let accountId: number | null = null;
    let firmId: number | null = null;
    let targetType: 'account' | 'firm';
    let targetId: string;

    if (args.scope === 'firm') {
      const fid = args.firmId;
      if (!fid || !(await hasFirmFinancialAccess(fid, uuid))) {
        throw new ForbiddenError('You do not have financial access to that firm.');
      }
      keyType = 'BUSINESS';
      firmId = fid;
      targetType = 'firm';
      targetId = `firm:${fid}`;
    } else {
      const personal = (await findAccountsForPlayer(uuid)).find(
        (a) => a.account_type === 'PERSONAL' && a.owner_uuid === uuid,
      );
      if (!personal) return { ok: false, error: 'You have no personal account.' };
      keyType = 'PERSONAL';
      accountId = personal.account_id;
      targetType = 'account';
      targetId = `account:${personal.account_id}`;
    }

    const secret = newSecret();
    await createSubscription({ ownerUuid: uuid, keyType, accountId, firmId, targetUrl: url, secret });
    await auditView(viewer, { method: 'POST', path: '/me/webhooks/create', targetType, targetId });
    revalidatePath('/me/webhooks');
    return { ok: true, secret };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function setWebhookActiveAction(id: number, active: boolean): Promise<WebhookActionResult> {
  const viewer = await getViewer();
  const uuid = requireLinkedUuid(viewer);
  try {
    const n = await setActive(id, uuid, active);
    if (n === 0) return { ok: false, error: 'Webhook not found.' };
    await auditView(viewer, { method: 'POST', path: '/me/webhooks/active', targetType: 'player', targetId: `webhook:${id}` });
    revalidatePath('/me/webhooks');
    revalidatePath(`/me/webhooks/${id}`);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function setWebhookUrlAction(id: number, url: string): Promise<WebhookActionResult> {
  const viewer = await getViewer();
  const uuid = requireLinkedUuid(viewer);
  try {
    await assertPublicHttpsUrl(url);
    const n = await setUrl(id, uuid, url.trim());
    if (n === 0) return { ok: false, error: 'Webhook not found.' };
    await auditView(viewer, { method: 'POST', path: '/me/webhooks/url', targetType: 'player', targetId: `webhook:${id}` });
    revalidatePath('/me/webhooks');
    revalidatePath(`/me/webhooks/${id}`);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function rotateWebhookSecretAction(id: number): Promise<WebhookActionResult> {
  const viewer = await getViewer();
  const uuid = requireLinkedUuid(viewer);
  try {
    const secret = newSecret();
    const n = await rotateSecret(id, uuid, secret);
    if (n === 0) return { ok: false, error: 'Webhook not found.' };
    await auditView(viewer, { method: 'POST', path: '/me/webhooks/rotate', targetType: 'player', targetId: `webhook:${id}` });
    revalidatePath(`/me/webhooks/${id}`);
    return { ok: true, secret };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function deleteWebhookAction(id: number): Promise<WebhookActionResult> {
  const viewer = await getViewer();
  const uuid = requireLinkedUuid(viewer);
  try {
    const n = await deleteSubscription(id, uuid);
    if (n === 0) return { ok: false, error: 'Webhook not found.' };
    await auditView(viewer, { method: 'POST', path: '/me/webhooks/delete', targetType: 'player', targetId: `webhook:${id}` });
    revalidatePath('/me/webhooks');
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

// ── Admin (fleet-wide management, not owner-scoped) ──────────────────────────

function requireAdmin(viewer: Viewer): asserts viewer is Extract<Viewer, { anon: false }> {
  if (viewer.anon || viewer.role !== 'admin') {
    throw new ForbiddenError('Admin only.');
  }
}

/** webhook_subscription.key_type is enum(PERSONAL,BUSINESS,GOVERNMENT); SYSTEM/unknown
 *  fall back to PERSONAL since the dispatcher matches by account_id, not key_type. */
function keyTypeForAccount(accountType: string): 'PERSONAL' | 'BUSINESS' | 'GOVERNMENT' {
  if (accountType === 'BUSINESS') return 'BUSINESS';
  if (accountType === 'GOVERNMENT') return 'GOVERNMENT';
  return 'PERSONAL';
}

/**
 * Admin: register an account-scoped webhook for an arbitrary account. Attributed
 * to the account's own owner (so it's model-consistent and the owner sees it in
 * /me/webhooks too); falls back to the admin if the account has no owner.
 */
export async function adminCreateWebhookAction(args: { accountId: number; url: string }): Promise<WebhookActionResult> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    await assertPublicHttpsUrl(args.url);
    const account = await findAccount(args.accountId);
    if (!account) return { ok: false, error: `Account #${args.accountId} not found.` };
    const ownerUuid = account.owner_uuid ?? viewer.minecraftUuid;
    if (!ownerUuid) return { ok: false, error: 'Account has no owner and you are not linked; cannot attribute the webhook.' };

    const secret = newSecret();
    await adminCreateForAccount({
      ownerUuid,
      keyType: keyTypeForAccount(account.account_type),
      accountId: args.accountId,
      targetUrl: args.url.trim(),
      secret,
    });
    await auditView(viewer, { method: 'POST', path: '/admin/webhooks/create', targetType: 'account', targetId: String(args.accountId) });
    revalidatePath('/admin/webhooks');
    return { ok: true, secret };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function adminSetWebhookActiveAction(id: number, active: boolean): Promise<WebhookActionResult> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    const n = await adminSetActive(id, active);
    if (n === 0) return { ok: false, error: 'Webhook not found.' };
    await auditView(viewer, { method: 'POST', path: '/admin/webhooks/active', targetType: 'global', targetId: `webhook:${id}` });
    revalidatePath('/admin/webhooks');
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}

export async function adminDeleteWebhookAction(id: number): Promise<WebhookActionResult> {
  const viewer = await getViewer();
  requireAdmin(viewer);
  try {
    const n = await adminDelete(id);
    if (n === 0) return { ok: false, error: 'Webhook not found.' };
    await auditView(viewer, { method: 'POST', path: '/admin/webhooks/delete', targetType: 'global', targetId: `webhook:${id}` });
    revalidatePath('/admin/webhooks');
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}
