// Lightweight liveness probe — returns 200 without touching the DB, so the
// container healthcheck doesn't run the heavy dashboard aggregates (which would
// turn DB slowness into a probe failure → pod restart, a cascading failure).
export const dynamic = 'force-dynamic';

export function GET() {
  return new Response('ok', {
    status: 200,
    headers: { 'content-type': 'text/plain', 'cache-control': 'no-store' },
  });
}
