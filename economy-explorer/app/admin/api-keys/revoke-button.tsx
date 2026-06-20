'use client';
import { useTransition, useState } from 'react';
import { revokeApiKeyAction } from '@/lib/actions/admin';

export function RevokeButton({ keyId }: { keyId: number }) {
  const [pending, start] = useTransition();
  const [err, setErr] = useState<string | null>(null);

  function onClick() {
    if (!confirm(`Revoke API key #${keyId}? Cannot be undone.`)) return;
    start(async () => {
      const r = await revokeApiKeyAction(keyId);
      if (!r.ok) setErr(r.error ?? 'Failed');
    });
  }

  return (
    <>
      <button className="btn" disabled={pending} onClick={onClick}>
        {pending ? 'Revoking…' : 'Revoke'}
      </button>
      {err && <div className="state-error" style={{ fontSize: 11 }}>{err}</div>}
    </>
  );
}
