import { NextResponse, type NextRequest } from 'next/server';

/**
 * Per-request access logging — feeds the bot-detection story from
 * scraping-mitigation-philosophy. One structured line per request.
 *
 * Auth status is observed via the session-cookie presence; we do not call
 * Auth.js here (middleware runs on the Edge runtime, Auth.js requires Node).
 * The full viewer state is logged by the page itself if it needs to.
 */
export function middleware(req: NextRequest) {
  const start = Date.now();
  const ua = req.headers.get('user-agent') ?? '';
  const ip = req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ?? '';
  const hasSession = req.cookies.has('authjs.session-token') || req.cookies.has('__Secure-authjs.session-token');

  // Theme preview override: ?theme=red|blue sets a cookie and redirects to
  // the same path without the param so the layout (which reads cookies on
  // the next render) picks it up. ?theme=clear drops the cookie.
  const themeParam = req.nextUrl.searchParams.get('theme');
  if (themeParam === 'red' || themeParam === 'blue' || themeParam === 'clear') {
    const url = req.nextUrl.clone();
    url.searchParams.delete('theme');
    const redirect = NextResponse.redirect(url);
    if (themeParam === 'clear') {
      redirect.cookies.delete('theme');
    } else {
      redirect.cookies.set('theme', themeParam, {
        path: '/',
        sameSite: 'lax',
        maxAge: 60 * 60 * 24 * 30,
      });
    }
    return redirect;
  }

  const res = NextResponse.next();

  // Structured access log — one line, JSON, picked up by Loki/Grafana.
  console.log(
    JSON.stringify({
      ts: new Date().toISOString(),
      kind: 'access',
      tenant: process.env.TENANT ?? 'unknown',
      method: req.method,
      path: req.nextUrl.pathname,
      query: req.nextUrl.search || undefined,
      ip,
      ua,
      hasSession,
      // Middleware's own synchronous work only — not request/render time.
      mwMs: Date.now() - start,
    }),
  );

  return res;
}

export const config = {
  matcher: [
    // Run on everything except static assets and the Auth.js endpoint itself.
    '/((?!_next/static|_next/image|favicon.ico|robots.txt|healthz|api/auth).*)',
  ],
};
