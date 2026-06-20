import { describe, it, expect, afterEach, vi } from 'vitest';
import { serverIdentity, blueMapUrl } from '@/lib/serverIdentity';

afterEach(() => vi.unstubAllEnvs());

describe('serverIdentity', () => {
  it('derives DemocracyCraft defaults for the blue theme', () => {
    const s = serverIdentity('blue');
    expect(s.name).toBe('DemocracyCraft');
    expect(s.ip).toBe('play.democracycraft.net');
    expect(s.map).toBe('https://map.democracycraft.net');
    expect(s.icon).toContain('dc-emblem');
  });

  it('derives StateCraft defaults for the red theme', () => {
    const s = serverIdentity('red');
    expect(s.name).toBe('StateCraft');
    expect(s.ip).toBe('play.mcstatecraft.com');
    expect(s.icon).toContain('sc-emblem');
  });

  it('lets env vars override every field and strips a trailing slash from MAP_URL', () => {
    vi.stubEnv('SERVER_NAME', 'TestCraft');
    vi.stubEnv('SERVER_IP', 'play.test.example');
    vi.stubEnv('SERVER_ICON', 'https://test.example/icon.png');
    vi.stubEnv('MAP_URL', 'https://map.test.example/');
    const s = serverIdentity('blue');
    expect(s).toEqual({
      name: 'TestCraft',
      ip: 'play.test.example',
      icon: 'https://test.example/icon.png',
      map: 'https://map.test.example',
    });
  });
});

describe('blueMapUrl', () => {
  it('builds a BlueMap anchor with the lowercased world and the default close zoom (60)', () => {
    expect(blueMapUrl('https://map.democracycraft.net', 'NewHamilton', 1308, 102, 2945)).toBe(
      'https://map.democracycraft.net/#newhamilton:1308:102:2945:60:0:0:0:1:flat',
    );
  });

  it('defaults a missing y to 64', () => {
    expect(blueMapUrl('https://m', 'world', 1, null, 3)).toBe('https://m/#world:1:64:3:60:0:0:0:1:flat');
  });

  it('returns null when world or x/z are missing', () => {
    expect(blueMapUrl('https://m', null, 1, 2, 3)).toBeNull();
    expect(blueMapUrl('https://m', 'world', null, 2, 3)).toBeNull();
    expect(blueMapUrl('https://m', 'world', 1, 2, null)).toBeNull();
  });
});
