'use client';
// Progressive enhancement for the API guide: the docs are statically generated
// and server-neutral, so the Swagger links for BOTH servers are rendered in the
// markdown. On the client we detect which server this Explorer belongs to (from
// the hostname) and emphasise the matching link, dim the other, and tag it
// "your server". On an unrecognised host (e.g. a dev domain) we leave both as-is.

import { usePathname } from 'next/navigation';
import { useEffect } from 'react';

function tenantFromHost(host: string): 'statecraft' | 'democracycraft' | null {
  const h = host.toLowerCase();
  if (h.includes('statecraft')) return 'statecraft';
  if (h.includes('democracycraft')) return 'democracycraft';
  return null;
}

export function ApiServerHint() {
  const path = usePathname();
  useEffect(() => {
    const tenant = tenantFromHost(window.location.hostname);
    if (!tenant) return;
    const links = Array.from(
      document.querySelectorAll<HTMLAnchorElement>('.doc-prose a[href*="swagger-ui"]'),
    );
    const badges: HTMLElement[] = [];
    for (const a of links) {
      const href = a.href.toLowerCase();
      const isCurrent =
        (tenant === 'statecraft' && (href.includes('mcstatecraft') || href.includes('statecraft'))) ||
        (tenant === 'democracycraft' && href.includes('democracycraft'));
      a.classList.add(isCurrent ? 'api-link-current' : 'api-link-other');
      if (isCurrent && !a.dataset.apiBadged) {
        a.dataset.apiBadged = '1';
        const badge = document.createElement('span');
        badge.className = 'api-link-badge';
        badge.textContent = 'your server';
        a.after(badge);
        badges.push(badge);
      }
    }
    return () => {
      badges.forEach((b) => b.remove());
      links.forEach((a) => {
        a.classList.remove('api-link-current', 'api-link-other');
        delete a.dataset.apiBadged;
      });
    };
  }, [path]);
  return null;
}
