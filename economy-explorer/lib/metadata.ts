import 'server-only';
import type { Metadata } from 'next';
import { resolveTheme } from '@/lib/theme';
import { serverIdentity } from '@/lib/serverIdentity';

/**
 * Per-instance metadata. The deployed server (DemocracyCraft *or* StateCraft —
 * never both) is resolved from the same THEME/SERVER_NAME env the rest of the
 * brand chrome uses, so titles, descriptions and OG tags name only the server
 * this instance actually serves.
 */

/** Public base URL for canonical/OG links — same resolution as robots/sitemap. */
export function siteUrl(): string {
  return (process.env.SITE_URL ?? process.env.AUTH_URL ?? 'http://localhost:3000').replace(/\/$/, '');
}

/** Resolve the deployed server's identity for this request. */
export async function brand() {
  return serverIdentity(await resolveTheme());
}

export interface PageMeta {
  /** Page-specific title segment, e.g. "Accounts". Omit for the site root. */
  title?: string;
  /** Page description. Use `{server}` as a placeholder for the server name. */
  description: string;
  /** Canonical path for this page, e.g. "/accounts" or "/accounts/123". */
  path?: string;
  /** Allow indexing (default false — the explorer is noindex except docs). */
  index?: boolean;
}

/**
 * Build a complete, server-branded {@link Metadata} object for a page. The
 * title is absolute ("<page> · <Server> Economy Explorer") so it does not rely
 * on parent title templates, and OG/Twitter tags are filled for link unfurls
 * (the site is noindex, so unfurls — not search — are the real audience).
 */
export async function buildMetadata({ title, description, path, index = false }: PageMeta): Promise<Metadata> {
  const id = await brand();
  const site = `${id.name} Economy Explorer`;
  const full = title ? `${title} · ${site}` : site;
  const desc = description.replaceAll('{server}', id.name);
  return {
    // Absolute so the parent layout's title template isn't re-applied (no double suffix).
    title: { absolute: full },
    description: desc,
    openGraph: {
      title: full,
      description: desc,
      siteName: site,
      type: 'website',
      images: [{ url: id.icon, alt: `${id.name} emblem` }],
      ...(path ? { url: path } : {}),
    },
    twitter: {
      card: 'summary',
      title: full,
      description: desc,
      images: [id.icon],
    },
    ...(path ? { alternates: { canonical: path } } : {}),
    ...(index ? { robots: { index: true, follow: true } } : {}),
  };
}
