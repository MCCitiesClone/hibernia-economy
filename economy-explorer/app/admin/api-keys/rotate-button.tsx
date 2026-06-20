'use client';
import { useTransition, useState } from 'react';
import { rotateApiKeyAction } from '@/lib/actions/admin';

export function RotateButton({ keyId }: { keyId: number }) {
  const [pending, start] = useTransition();
  const [err, setErr] = useState<string | null>(null);

  function onClick() {
    if (!confirm(`Force-rotate key #${keyId}? The current token stops working; the owner must re-export it in-game.`)) return;
    start(async () => {
      const r = await rotateApiKeyAction(keyId);
      if (!r.ok) setErr(r.error ?? 'Failed');
      else setErr(null);
    });
  }

  return (
    <>
      <button className="btn" disabled={pending} onClick={onClick} style={{ padding: '3px 8px', fontSize: 12 }}>
        {pending ? 'Rotating…' : 'Rotate'}
      </button>
      {err && <div className="state-error" style={{ fontSize: 11 }}>{err}</div>}
    </>
  );
}
