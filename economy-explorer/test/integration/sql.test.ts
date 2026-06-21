import { describe, it, expect, beforeAll } from 'vitest';
import { HAS_DB, resetDb, ALICE, BOB, CAROL, DAVE, BEDROCK, SECRETARY } from './db';
import { accountLabel, looksLikeUuid } from '@/lib/format';
import { findAccount, findPostingsByTxnId, getTotalSupply, getPersonalSupply, canReadAccount } from '@/lib/sql/ledger';
import { getPersonalBalances, getBalanceDistribution } from '@/lib/sql/stats';
import { isFirmMember, hasFirmFinancialAccess, getAccountFirmId, getFirmStats, findFirmByDisplayName } from '@/lib/sql/firm';
import { findCapabilities, findPlayerUuidByName } from '@/lib/sql/group';
import { findAccountsForPlayer } from '@/lib/sql/me';
import { findIdentityBySub } from '@/lib/sql/identity';
import { getMoneyFlow } from '@/lib/sql/moneyFlow';
import { listItemSales } from '@/lib/sql/market';
import { gini } from '@/lib/derived';
import { audit } from '@/lib/audit';
import { listAudit } from '@/lib/sql/audit';

// Real queries against a real MariaDB (no mocking). Skipped unless RUN_INTEGRATION=1.
const d = HAS_DB ? describe : describe.skip;

beforeAll(async () => {
  if (HAS_DB) await resetDb();
}, 60_000);

d('account name resolution', () => {
  it('resolves a personal account whose display_name is its UUID to the player name', async () => {
    const a = await findAccount(1); // Alice, display_name = uuid
    expect(a).not.toBeNull();
    expect(looksLikeUuid(a!.display_name)).toBe(true);
    expect(a!.owner_name).toBe('Alice');
    expect(accountLabel(a!)).toBe('Alice');
    expect(a!.balance).toBe('5000.00');
  });

  it('keeps a real business display name', async () => {
    const a = await findAccount(3);
    expect(accountLabel(a!)).toBe('Acme Corp');
  });

  it('falls back to a short UUID for a player not in firm_players (Dave)', async () => {
    const a = await findAccount(7);
    expect(a!.owner_name).toBeNull();
    expect(accountLabel(a!)).toMatch(/…$/); // shortened UUID
    expect(looksLikeUuid(accountLabel(a!))).toBe(false);
  });
});

d('transaction postings resolve names (the HIGH bug fix)', () => {
  it('never renders a raw UUID for a posting account', async () => {
    const postings = await findPostingsByTxnId(1); // Alice personal -> Acme business
    expect(postings).toHaveLength(2);
    const labels = postings.map((p) => accountLabel(p));
    expect(labels).toContain('Alice');
    expect(labels).toContain('Acme Corp');
    for (const l of labels) expect(looksLikeUuid(l)).toBe(false);
  });
});

d('supply, balances, gini, distribution', () => {
  it('excludes SYSTEM and archived from supply', async () => {
    expect(await getPersonalSupply()).toBe('106500.00'); // 5000 + 100000 + 1500
    expect(await getTotalSupply()).toBe('139500.00'); // + business 25000 + gov 8000, minus SYSTEM
  });

  it('returns only positive active personal balances and a sensible gini', async () => {
    const balances = (await getPersonalBalances()).sort((a, b) => a - b);
    expect(balances).toEqual([1500, 5000, 100000]);
    expect(gini(balances)).toBeGreaterThan(0.5); // one rich account dominates
  });

  it('buckets balances into the finer bands', async () => {
    const dist = await getBalanceDistribution();
    const count = (k: string) => dist.find((b) => b.bucket === k)?.account_count ?? 0;
    expect(count('1k_2k5')).toBe(1); // 1500 (1K–2.5K)
    expect(count('5k_10k')).toBe(1); // 5000 lands here (boundary is balance < 5000)
    expect(count('100k_plus')).toBe(1); // 100000
    expect(dist.every((b) => typeof b.bucket_label === 'string' && b.bucket_label.length > 0)).toBe(true);
  });
});

d('firm financial-access tiers', () => {
  it('isFirmMember for current employees only', async () => {
    expect(await isFirmMember(1, ALICE)).toBe(true);
    expect(await isFirmMember(1, BOB)).toBe(true);
    expect(await isFirmMember(1, DAVE)).toBe(false);
  });

  it('hasFirmFinancialAccess requires the FINANCIAL/ADMIN role permission', async () => {
    expect(await hasFirmFinancialAccess(1, ALICE)).toBe(true); // Owner: ADMIN
    expect(await hasFirmFinancialAccess(1, BOB)).toBe(true); // Finance: FINANCIAL
    expect(await hasFirmFinancialAccess(1, CAROL)).toBe(false); // Worker: DEFAULT only
    expect(await hasFirmFinancialAccess(1, DAVE)).toBe(false); // not an employee
  });

  it('maps firm accounts and reports firm stats', async () => {
    expect(await getAccountFirmId(3)).toBe(1); // Acme business account
    expect(await getAccountFirmId(1)).toBeNull(); // personal account
    const s = await getFirmStats();
    expect(s.businessWealth).toBe('25000.00'); // only Acme has a business account
    expect(s.activeFirms).toBe(2); // Acme + TaxFree Co
    expect(s.activeEmployees).toBe(3); // TaxFree Co has no employees
    expect(s.newThisMonth).toBe(2);
    expect(s.avgEmployees).toBeCloseTo(1.5, 5); // 3 employees / 2 firms
  });

  it('surfaces balance-tax exemption, ignoring soft-deleted flags', async () => {
    const exempt = await findFirmByDisplayName('TaxFree Co');
    expect(exempt?.exempt).toBe(true);

    // Acme's exempt flag is soft-deleted (deleted_at set) → must read as not exempt.
    const acme = await findFirmByDisplayName('Acme Corp');
    expect(acme?.exempt).toBe(false);
  });
});

d('money flow counts only clean cross-type transfers', () => {
  it('includes the 2-leg cross-type txn and excludes same-type + multi-leg', async () => {
    const edges = await getMoneyFlow(30);
    expect(edges).toHaveLength(1); // txn2 is same-type, txn3 is 3-leg
    expect(edges[0]).toMatchObject({ from_type: 'PERSONAL', to_type: 'BUSINESS', txn_count: 1 });
    expect(edges[0].amount).toBe('500.00');
  });
});

d('group capabilities', () => {
  it('returns capabilities a player has via group membership', async () => {
    // Bob is a member of the "Auditors" group (grants staff.audit).
    expect(await findCapabilities(BOB)).toContain('staff.audit');
  });

  it('returns nothing for a player in no groups', async () => {
    expect(await findCapabilities(CAROL)).toEqual([]);
  });
});

d('audit logging records privileged access', () => {
  it('persists an audit row that listAudit reads back', async () => {
    await audit({
      viewer: { anon: false, keycloakSub: 'e2e-admin', minecraftUuid: ALICE, minecraftName: 'Alice', linked: true, role: 'admin', capabilities: ['admin', 'staff.audit', 'government'] },
      method: 'GET',
      path: '/transactions',
      targetType: 'global',
      targetId: null,
      outcome: 200,
      sourceIp: '203.0.113.7',
    });
    const rows = await listAudit({ actor: null, targetType: null, limit: 50, offset: 0 });
    const row = rows.find((r) => r.path === '/transactions');
    expect(row).toBeTruthy();
    expect(row!.actor_role).toBe('admin');
  });
});

d('market seller names resolve (no UUIDs)', () => {
  it('shows player and firm names, never a raw UUID', async () => {
    const sales = await listItemSales('DIAMOND', 50, 0);
    expect(sales.length).toBe(2);
    const names = sales.map((s) => s.owner_name);
    expect(names).toContain('Alice'); // personal shop, display_name was a UUID
    expect(names).toContain('Acme Corp'); // firm shop
    for (const n of names) expect(looksLikeUuid(n)).toBe(false);
  });
});

// PAR-240: a Bedrock/Floodgate player who completes the link must reach a working,
// correctly-named wallet. The flow already binds the in-game (Floodgate) UUID; this
// locks in that the explorer's DAL resolves it end to end.
d('Bedrock / Floodgate linked wallet (PAR-240)', () => {
  it('resolves the durable identity to the Floodgate UUID + dotted name', async () => {
    const id = await findIdentityBySub('e2e-bedrock');
    expect(id).not.toBeNull();
    expect(id!.playerUuid).toBe(BEDROCK);
    expect(id!.minecraftName).toBe('.BedrockBob');
  });

  it('resolves the wallet with the dotted name, never a bare UUID', async () => {
    const a = (await findAccountsForPlayer(BEDROCK)).find((x) => x.account_id === 8);
    expect(a).toBeTruthy();
    expect(a!.owner_name).toBe('.BedrockBob');
    expect(accountLabel(a!)).toBe('.BedrockBob');
    expect(looksLikeUuid(accountLabel(a!))).toBe(false);
  });

  it('finds a Floodgate player by their dotted name (case-insensitive)', async () => {
    expect(await findPlayerUuidByName('.BedrockBob')).toBe(BEDROCK);
    expect(await findPlayerUuidByName('.BEDROCKBOB')).toBe(BEDROCK);
  });
});

// PAR-237: a government department "secretary" is a read-only viewer of their
// department account. The explorer must let them see that account's ledger
// history (scoped to the account, never blanket), so canReadAccount drives the
// account-detail history gate.
d('government account viewer access (PAR-237)', () => {
  it('grants a read-only viewer access to their department account', async () => {
    expect(await canReadAccount(5, SECRETARY)).toBe(true); // City Hall (GOVERNMENT)
  });

  it('does not grant access to an unrelated account', async () => {
    expect(await canReadAccount(1, SECRETARY)).toBe(false); // Alice's personal account
  });

  it('denies a player with no member/authorizer/viewer row', async () => {
    expect(await canReadAccount(5, DAVE)).toBe(false);
  });
});
