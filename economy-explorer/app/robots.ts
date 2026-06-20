import type { MetadataRoute } from 'next';
import { headers } from 'next/headers';

// Site-wide crawl policy. Everything stays disallowed (the anti-scraping
// posture) EXCEPT the public /docs documentation site. The base URL is derived
// from the request Host so the Sitemap link is correct in every environment.
export const dynamic = 'force-dynamic';

async function baseUrl(): Promise<string> {
  const h = await headers();
  const host = h.get('x-forwarded-host') ?? h.get('host');
  if (host) {
    const proto = h.get('x-forwarded-proto') ?? (host.startsWith('localhost') ? 'http' : 'https');
    return `${proto}://${host}`;
  }
  return (process.env.SITE_URL ?? process.env.AUTH_URL ?? 'http://localhost:3000').replace(/\/$/, '');
}

export default async function robots(): Promise<MetadataRoute.Robots> {
  const base = await baseUrl();
  return {
    rules: [{ userAgent: '*', allow: '/docs', disallow: '/' }],
    sitemap: `${base}/sitemap.xml`,
  };
}
