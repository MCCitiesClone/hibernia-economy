import 'server-only';

/**
 * Per-API-key usage pulled from the cluster's Prometheus
 * (`treasury_api_key_requests_total` series scraped from Spring's
 * `/actuator/prometheus`). Two queries mirror PrometheusQueryService.keyUsage:
 *
 * - 24h totals split by outcome
 *   sum by (key_id, outcome) (increase(treasury_api_key_requests_total{job="<job>"}[24h]))
 * - 5m rate (req/min)
 *   sum by (key_id) (rate(treasury_api_key_requests_total{job="<job>"}[5m])) * 60
 *
 * The `{job=...}` selector is LOAD-BEARING: key_id is auto-increment per
 * treasury DB, so each server has its own key #1, #2, … and the metric carries
 * no key-owner identity beyond key_id. Without scoping to this tenant's own
 * treasury job, `sum by (key_id)` merges both servers' key #N into one bucket
 * and the numbers get painted onto whichever owner that key_id maps to in the
 * local DB — e.g. StateCraft key #10's throttling showing up on the
 * DemocracyCraft explorer's key #10 owner. The job label distinguishes the
 * servers (treasury-rest-api{,-democracycraft,-statecraft}); TENANT selects it.
 *
 * Disabled (returns empty map) when PROMETHEUS_URL is unset, and fails soft
 * on any query/parse error so the admin page still renders without numbers.
 */
export interface KeyUsage {
  requests24h: number;
  throttled24h: number;
  reqPerMin: number;
}

/**
 * Prometheus `job` label for this tenant's treasury-rest-api scrape target.
 * Mirrors the deployment naming: dev → treasury-rest-api, otherwise
 * treasury-rest-api-<tenant> (see k8s-gitops economy-explorer overlays).
 */
function treasuryJob(): string {
  const tenant = process.env.TENANT?.trim().toLowerCase();
  return tenant && tenant !== 'dev' ? `treasury-rest-api-${tenant}` : 'treasury-rest-api';
}

export async function queryKeyUsage(): Promise<Map<number, KeyUsage>> {
  const base = process.env.PROMETHEUS_URL?.trim();
  if (!base) return new Map();

  const job = treasuryJob();
  const sel = `treasury_api_key_requests_total{job="${job}"}`;
  const out = new Map<number, { allowed: number; throttled: number; rate: number }>();

  try {
    // The two queries are independent — run them concurrently so a slow
    // Prometheus costs one timeout, not two back-to-back (worst case 6s → 3s).
    const [r1, r2] = await Promise.all([
      query(base, `sum by (key_id, outcome) (increase(${sel}[24h]))`),
      query(base, `sum by (key_id) (rate(${sel}[5m])) * 60`),
    ]);
    for (const s of r1.data?.result ?? []) {
      const keyId = parseInt(String(s.metric?.key_id ?? ''), 10);
      if (!Number.isFinite(keyId)) continue;
      const outcome = String(s.metric?.outcome ?? '');
      const v = Math.round(parseFloat(s.value?.[1] ?? '0'));
      const rec = out.get(keyId) ?? { allowed: 0, throttled: 0, rate: 0 };
      if (outcome === 'throttled') rec.throttled += v;
      else rec.allowed += v;
      out.set(keyId, rec);
    }

    for (const s of r2.data?.result ?? []) {
      const keyId = parseInt(String(s.metric?.key_id ?? ''), 10);
      if (!Number.isFinite(keyId)) continue;
      const rate = parseFloat(s.value?.[1] ?? '0');
      const rec = out.get(keyId) ?? { allowed: 0, throttled: 0, rate: 0 };
      rec.rate = rate;
      out.set(keyId, rec);
    }
  } catch (e) {
    console.warn('[prometheus] keyUsage query failed:', e);
    return new Map();
  }

  const result = new Map<number, KeyUsage>();
  for (const [k, v] of out) {
    result.set(k, { requests24h: v.allowed, throttled24h: v.throttled, reqPerMin: v.rate });
  }
  return result;
}

interface PromInstant {
  data?: { result?: { metric?: Record<string, string>; value?: [number, string] }[] };
}

async function query(base: string, promql: string): Promise<PromInstant> {
  const url = `${base.replace(/\/$/, '')}/api/v1/query?query=${encodeURIComponent(promql)}`;
  // Hard timeout so a slow/blackholed Prometheus can't hang the admin page.
  const res = await fetch(url, { cache: 'no-store', signal: AbortSignal.timeout(1500) });
  if (!res.ok) throw new Error(`prometheus ${res.status}`);
  return (await res.json()) as PromInstant;
}
