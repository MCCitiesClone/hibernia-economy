'use client';

import { useEffect } from 'react';
import { initializeFaro, getWebInstrumentations } from '@grafana/faro-web-sdk';

// Grafana Faro RUM (Core Web Vitals + JS errors) → the in-cluster Alloy
// faro.receiver (faro.paradaux.io), which lands them in Loki. The tenant is
// encoded in app.name (`economy-explorer-<tenant>`) so each prod instance is
// queryable individually and as an aggregate (economy-explorer-*). Rendered
// from the server layout only when FARO_COLLECTOR_URL is set (prod overlays),
// mirroring the Umami wiring. Single image, multiple tenants → the tenant is a
// runtime prop from process.env.TENANT, not a build-time NEXT_PUBLIC_ value.
let started = false;

export function FaroAnalytics({ appName, url }: { appName: string; url: string }) {
  useEffect(() => {
    if (started || typeof window === 'undefined') return;
    started = true;
    initializeFaro({
      url,
      app: { name: appName, environment: 'production' },
      // Default web instrumentations: Web Vitals (LCP/FCP/CLS/TTFB/INP),
      // uncaught errors, session + view (route) tracking.
      instrumentations: [...getWebInstrumentations()],
    });
  }, [appName, url]);

  return null;
}
