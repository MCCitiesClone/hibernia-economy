import { NextResponse } from 'next/server';
import { getViewer } from '@/lib/auth/viewer';

// Client-side viewer resolution for the header (login state, role badges, nav
// gating). Keeping this out of the root layout is what lets the layout — and so
// static pages like /docs — render without reading the session cookie. Per-user,
// so never cached at the HTTP layer.
export const dynamic = 'force-dynamic';

export async function GET() {
  const v = await getViewer();
  return NextResponse.json(
    {
      anon: v.anon,
      loggedIn: !v.anon,
      isAdmin: v.role === 'admin',
      role: v.anon ? null : v.role,
      minecraftName: v.anon ? null : v.minecraftName,
    },
    { headers: { 'Cache-Control': 'private, no-store' } },
  );
}
