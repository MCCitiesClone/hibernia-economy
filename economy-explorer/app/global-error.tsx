'use client';
// Catches errors thrown in the ROOT layout itself (e.g. getViewer/DB failure),
// which the route-segment error.tsx can't reach. Must render its own <html>/
// <body> because it replaces the whole document. Inline styles only (the app
// CSS may not be available here) — matches the default dark theme.
export default function GlobalError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <html lang="en">
      <body
        style={{
          margin: 0,
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#0c0e14',
          color: '#edeff5',
          fontFamily: 'system-ui, -apple-system, sans-serif',
        }}
      >
        <div style={{ textAlign: 'center', padding: 24, maxWidth: 460 }}>
          <h1 style={{ fontSize: 22, fontWeight: 600, margin: '0 0 8px' }}>Something went wrong</h1>
          <p style={{ color: '#848b9c', margin: '0 0 18px', lineHeight: 1.5 }}>
            The explorer couldn&rsquo;t load. This is usually temporary — please try again.
          </p>
          <button
            type="button"
            onClick={() => reset()}
            style={{
              padding: '9px 18px',
              borderRadius: 8,
              border: '1px solid #2b3550',
              background: '#3b6fe0',
              color: '#fff',
              fontSize: 14,
              cursor: 'pointer',
            }}
          >
            Try again
          </button>
        </div>
      </body>
    </html>
  );
}
