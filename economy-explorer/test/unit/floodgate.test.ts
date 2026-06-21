import { describe, it, expect } from 'vitest';
import { uuidToBin, binToUuid } from '@/lib/db';
import { accountLabel, looksLikeUuid } from '@/lib/format';

// PAR-240: a Bedrock/Floodgate player holds a Floodgate UUID (high 8 bytes
// all-zero, XUID in the low 8) and a '.'-prefixed name. The explorer already
// treats both as first-class — the whole identity/display layer is UUID-version
// agnostic — so a linked Bedrock player gets a working, correctly-named wallet.
// These guard that the pure identity/display layer can't silently regress (e.g.
// someone later adds a v4-UUID assumption or a `^\w+$` name rule, the exact
// class of Bedrock-hostile bug seen in ChestShop, PAR-109).
const FLOODGATE_UUID = '00000000-0000-0000-0000-00000000bed0';

describe('Floodgate identity — display & UUID handling (PAR-240)', () => {
  it('round-trips a Floodgate-shaped UUID through BINARY(16)', () => {
    const bin = uuidToBin(FLOODGATE_UUID);
    expect(bin).toHaveLength(16);
    expect(binToUuid(bin)).toBe(FLOODGATE_UUID);
  });

  it('recognises a Floodgate UUID as a UUID (so a junk UUID display_name is hidden)', () => {
    expect(looksLikeUuid(FLOODGATE_UUID)).toBe(true);
  });

  it('does not mistake a dotted Bedrock name for a UUID', () => {
    expect(looksLikeUuid('.BedrockBob')).toBe(false);
  });

  it('labels a Floodgate account with its dotted name, not a shortened UUID', () => {
    const label = accountLabel({
      display_name: FLOODGATE_UUID, // personal accounts default display_name to the raw UUID
      owner_name: '.BedrockBob',
      account_id: 8,
    });
    expect(label).toBe('.BedrockBob');
    expect(looksLikeUuid(label)).toBe(false);
  });

  it('falls back to a shortened UUID (never a raw one) when a Floodgate player has no cached name', () => {
    const label = accountLabel({ display_name: FLOODGATE_UUID, owner_name: null, account_id: 8 });
    expect(looksLikeUuid(label)).toBe(false);
    expect(label).toMatch(/…$/);
  });
});
