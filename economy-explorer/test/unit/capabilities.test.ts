import { describe, it, expect } from 'vitest';
import {
  CAPABILITIES,
  CAPABILITY_LABELS,
  CAPABILITY_DESCRIPTIONS,
  legacyRoleCapabilities,
  roleFromCapabilities,
  isCapability,
} from '@/lib/auth/capabilities';

describe('capability metadata stays in lockstep with the vocabulary', () => {
  it('every capability has a label and a non-trivial description', () => {
    for (const c of CAPABILITIES) {
      expect(CAPABILITY_LABELS[c], `label for ${c}`).toBeTruthy();
      expect((CAPABILITY_DESCRIPTIONS[c] ?? '').length, `description for ${c}`).toBeGreaterThan(20);
    }
  });
});

describe('legacyRoleCapabilities', () => {
  it('admin implies every capability', () => {
    expect(legacyRoleCapabilities('admin')).toEqual(
      expect.arrayContaining(['admin', 'staff.audit', 'government']),
    );
  });

  it('government implies staff.audit + government but not admin', () => {
    const caps = legacyRoleCapabilities('government');
    expect(caps).toContain('staff.audit');
    expect(caps).toContain('government');
    expect(caps).not.toContain('admin');
  });

  it('player implies nothing', () => {
    expect(legacyRoleCapabilities('player')).toEqual([]);
  });
});

describe('roleFromCapabilities', () => {
  it('admin capability collapses to admin role', () => {
    expect(roleFromCapabilities(['admin'])).toBe('admin');
  });

  it('government without admin collapses to government role', () => {
    expect(roleFromCapabilities(['staff.audit', 'government'])).toBe('government');
  });

  it('staff.audit alone (a DOC auditor) stays player role', () => {
    // The capability grants audit access (isStaff) without elevating the role,
    // so requireRole('government') still keeps DOC out of government-only pages.
    expect(roleFromCapabilities(['staff.audit'])).toBe('player');
  });

  it('empty set is player', () => {
    expect(roleFromCapabilities([])).toBe('player');
  });
});

describe('isCapability', () => {
  it('accepts known capabilities and rejects unknown strings', () => {
    expect(isCapability('staff.audit')).toBe(true);
    expect(isCapability('admin')).toBe(true);
    expect(isCapability('not-a-capability')).toBe(false);
  });
});
