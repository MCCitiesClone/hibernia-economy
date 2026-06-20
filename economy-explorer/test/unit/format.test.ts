import { describe, it, expect } from 'vitest';
import {
  accountLabel,
  looksLikeUuid,
  shortenUuid,
  fmtN,
  fmtAmt,
  fmtAmtFull,
  fmtPct,
} from '@/lib/format';

const UUID = '25a9c52a-6af0-45d8-a52d-e4d5cfcbbab5';

describe('looksLikeUuid', () => {
  it('recognises canonical UUIDs (any case, trimmed)', () => {
    expect(looksLikeUuid(UUID)).toBe(true);
    expect(looksLikeUuid(UUID.toUpperCase())).toBe(true);
    expect(looksLikeUuid(`  ${UUID}  `)).toBe(true);
  });
  it('rejects names and partials', () => {
    expect(looksLikeUuid('Paradaux')).toBe(false);
    expect(looksLikeUuid('25a9c52a')).toBe(false);
    expect(looksLikeUuid(null)).toBe(false);
    expect(looksLikeUuid(undefined)).toBe(false);
    expect(looksLikeUuid('')).toBe(false);
  });
});

describe('accountLabel', () => {
  it('prefers a real (non-UUID) display name', () => {
    expect(accountLabel({ display_name: 'Acme Corp', owner_name: 'Bob', owner_uuid: UUID, account_id: 1 })).toBe('Acme Corp');
  });

  it('skips a UUID-shaped display name and uses the resolved player name', () => {
    // personal accounts default display_name to the owner UUID
    expect(accountLabel({ display_name: UUID, owner_name: 'Paradaux', owner_uuid: UUID, account_id: 2 })).toBe('Paradaux');
  });

  it('falls back to a short UUID when only the UUID is known', () => {
    expect(accountLabel({ display_name: UUID, owner_name: null, owner_uuid: UUID, account_id: 3 })).toBe('25a9c52a…');
    expect(accountLabel({ display_name: null, owner_name: null, owner_uuid: UUID, account_id: 4 })).toBe('25a9c52a…');
  });

  it('falls back to Account #N when nothing is known', () => {
    expect(accountLabel({ display_name: null, owner_name: null, owner_uuid: null, account_id: 42 })).toBe('Account #42');
  });
});

describe('shortenUuid', () => {
  it('keeps the first 8 chars with an ellipsis', () => {
    expect(shortenUuid(UUID)).toBe('25a9c52a…');
    expect(shortenUuid(null)).toBe('—');
  });
});

describe('number / money / percent formatting', () => {
  it('fmtN groups thousands', () => {
    expect(fmtN(1234567)).toBe('1,234,567');
    expect(fmtN(0)).toBe('0');
  });

  it('fmtAmt abbreviates with K/M/B and a real minus sign', () => {
    expect(fmtAmt(950)).toBe('$950.00');
    expect(fmtAmt(1500)).toBe('$1.5K');
    expect(fmtAmt(12_010_000)).toBe('$12.01M');
    expect(fmtAmt(2_500_000_000)).toBe('$2.50B');
    expect(fmtAmt(-1500)).toBe('−$1.5K');
    expect(fmtAmt('not-a-number')).toBe('—');
  });

  it('fmtAmtFull shows exact 2dp grouped', () => {
    expect(fmtAmtFull(1234567.5)).toBe('$1,234,567.50');
    expect(fmtAmtFull(-42)).toBe('−$42.00');
    expect(fmtAmtFull('abc')).toBe('—');
  });

  it('fmtPct', () => {
    expect(fmtPct(0.5)).toBe('50.0%');
    expect(fmtPct(0.1234, 2)).toBe('12.34%');
  });
});
