'use client';
import { useState, useTransition } from 'react';
import { linkStart, type LinkStartResult } from '@/lib/actions/link';

export function LinkStartForm() {
  const [pending, start] = useTransition();
  const [result, setResult] = useState<LinkStartResult | null>(null);

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    start(async () => {
      const r = await linkStart();
      setResult(r);
    });
  }

  return (
    <div className="card">
      <div className="card-title">Generate a code</div>
      <form onSubmit={onSubmit} style={{ padding: '4px 0 8px' }}>
        <button type="submit" className="btn btn-primary" disabled={pending}>
          {pending ? 'Generating…' : 'Generate code'}
        </button>
      </form>

      {result?.code && (
        <div className="state-empty" style={{ padding: 24, textAlign: 'center' }}>
          <div className="kpi-label" style={{ marginBottom: 6 }}>Your code</div>
          <div className="mono" style={{ fontSize: 32, fontWeight: 700, letterSpacing: 4 }}>{result.code}</div>
          <div className="kpi-meta" style={{ marginTop: 10 }}>
            Run{' '}
            <span className="mono">/treasuryapi ui link {result.code}</span>{' '}
            in-game within 5 minutes.
          </div>
        </div>
      )}

      {result?.alreadyLinked && (
        <div className="state-empty" style={{ padding: 16 }}>{result.message}</div>
      )}

      {result?.error && <div className="state-error">{result.error}</div>}
    </div>
  );
}
