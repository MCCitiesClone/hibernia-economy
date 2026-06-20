// Per-server identity for the brand chrome. Each field falls back to a
// theme-derived default when its env var is unset, so a fresh deploy is
// correct without extra config:
//   blue → DemocracyCraft, red → StateCraft.
// Env overrides: SERVER_NAME, SERVER_IP, SERVER_ICON, MAP_URL.

const DERIVED: Record<'blue' | 'red', { name: string; ip: string; icon: string; map: string }> = {
  blue: {
    name: 'DemocracyCraft',
    ip: 'play.democracycraft.net',
    icon: 'https://www.democracycraft.net/data/assets/logo_alternate/dc-emblem.png',
    map: 'https://map.democracycraft.net',
  },
  red: {
    name: 'StateCraft',
    ip: 'play.mcstatecraft.com',
    icon: 'https://www.mcstatecraft.com/data/assets/logo_alternate/sc-emblem.png',
    map: 'https://map.mcstatecraft.com',
  },
};

export interface ServerIdentity {
  name: string;
  ip: string;
  icon: string;
  /** BlueMap base URL (no trailing slash). */
  map: string;
}

export function serverIdentity(theme: 'blue' | 'red'): ServerIdentity {
  const d = DERIVED[theme];
  return {
    name: process.env.SERVER_NAME?.trim() || d.name,
    ip: process.env.SERVER_IP?.trim() || d.ip,
    icon: process.env.SERVER_ICON?.trim() || d.icon,
    map: (process.env.MAP_URL?.trim() || d.map).replace(/\/$/, ''),
  };
}

/**
 * Camera distance (blocks) for a shop deep link. BlueMap has no "highlight this
 * block" URL param — the hash is camera state only — so we approximate a
 * selection by framing the target block dead-centre at a close distance. ~60
 * drops the camera onto the shop building with the block centred while keeping
 * a little surrounding context for orientation. Override with MAP_ZOOM.
 */
const MAP_ZOOM = (() => {
  const n = Number(process.env.MAP_ZOOM);
  return Number.isFinite(n) && n > 0 ? n : 60;
})();

/**
 * Deep link into BlueMap centred on a world coordinate. Anchor format mirrors
 * a live BlueMap URL: #<map>:<x>:<y>:<z>:<dist>:0:0:0:1:flat. The Bukkit world
 * name is lowercased to match the BlueMap map id (overworld/city worlds map
 * 1:1 on these servers); if it doesn't match, BlueMap just opens its default
 * map. Returns null when coords/world are missing.
 */
export function blueMapUrl(
  mapBase: string,
  world: string | null | undefined,
  x: number | null | undefined,
  y: number | null | undefined,
  z: number | null | undefined,
): string | null {
  if (!world || x == null || z == null) return null;
  const map = world.toLowerCase();
  return `${mapBase}/#${map}:${x}:${y ?? 64}:${z}:${MAP_ZOOM}:0:0:0:1:flat`;
}
