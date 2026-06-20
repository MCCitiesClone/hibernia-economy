import 'server-only';

// Live player count via a Minecraft server-list query. Uses the public
// mcsrvstat.us API (no key, caches server-side) rather than implementing the
// raw SLP protocol from the pod. Fails safe: on any error the footer simply
// omits the count. Override the endpoint with MC_STATUS_API.

export interface ServerStatus {
  online: boolean;
  players: number | null;
  max: number | null;
}

const OFFLINE: ServerStatus = { online: false, players: null, max: null };

export async function serverStatus(ip: string): Promise<ServerStatus> {
  const base = process.env.MC_STATUS_API?.trim() || 'https://api.mcsrvstat.us/3';
  try {
    const res = await fetch(`${base}/${encodeURIComponent(ip)}`, {
      // Cache per-pod for 60s so we never hammer the status API.
      next: { revalidate: 60 },
      signal: AbortSignal.timeout(4000),
    });
    if (!res.ok) return OFFLINE;
    const j = (await res.json()) as { online?: boolean; players?: { online?: number; max?: number } };
    return {
      online: !!j.online,
      players: j.players?.online ?? null,
      max: j.players?.max ?? null,
    };
  } catch {
    return OFFLINE;
  }
}
