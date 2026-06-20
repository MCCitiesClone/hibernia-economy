import type { MetadataRoute } from 'next';
import { headers } from 'next/headers';
import { getAllDocSlugs, isAdminDoc } from '@/lib/docs';

// Only the public /docs pages are listed — the rest of the app is noindex.
// Dynamic so the absolute URLs use the real request Host per environment.
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

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const base = await baseUrl();
  // Admin docs are gated — never list them for crawlers.
  const slugs = (await getAllDocSlugs()).filter((slug) => !isAdminDoc(slug));
  return slugs.map((slug) => ({
    url: `${base}/docs${slug.length ? '/' + slug.join('/') : ''}`,
    changeFrequency: 'weekly',
    priority: slug.length ? 0.6 : 0.8,
  }));
}
