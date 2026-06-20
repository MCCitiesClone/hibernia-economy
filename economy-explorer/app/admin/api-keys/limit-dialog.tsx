'use client';
import { useState, useTransition } from 'react';
import { setRateLimitAction } from '@/lib/actions/admin';

interface LimitDialogProps {
  ownerUuid: string;
  ownerName: string;
  currentMultiplier: string;
}

export function LimitDialog({ ownerUuid, ownerName, currentMultiplier }: LimitDialogProps) {
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState(currentMultiplier);
  const [note, setNote] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [pending, start] = useTransition();

  function save() {
    const mult = parseFloat(value);
    if (!Number.isFinite(mult) || mult <= 0) {
      setErr('Enter a positive multiplier (1 = default).');
      return;
    }
    start(async () => {
      const r = await setRateLimitAction({ ownerUuid, multiplier: mult, note: note || null });
      if (!r.ok) setErr(r.error ?? 'Failed');
      else { setErr(null); setOpen(false); }
    });
  }

  return (
    <>
      <button className="btn" style={{ padding: '3px 8px', fontSize: 12 }} onClick={() => setOpen(true)}>
        Limit
      </button>
      {open && (
        <div className="modal-backdrop" onClick={() => setOpen(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="card-title">Rate limit · {ownerName}</div>
            <p style={{ color: 'var(--fg-soft)', fontSize: 13, marginTop: 4 }}>
              Multiplier applied to <strong>all</strong> of this issuer&rsquo;s keys.
              1 = default; raise for a trusted operator.
            </p>
            <label style={{ display: 'block', fontSize: 12, color: 'var(--fg-muted)', marginTop: 10 }}>Multiplier</label>
            <input
              type="number"
              min="0.1"
              step="0.5"
              className="filter-select"
              style={{ width: 120 }}
              value={value}
              onChange={(e) => setValue(e.target.value)}
            />
            <label style={{ display: 'block', fontSize: 12, color: 'var(--fg-muted)', marginTop: 10 }}>Note (optional)</label>
            <input
              type="text"
              className="filter-select"
              style={{ width: '100%' }}
              placeholder="why this limit was changed"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
            {err && <div className="state-error" style={{ fontSize: 12, marginTop: 8 }}>{err}</div>}
            <div style={{ display: 'flex', gap: 8, marginTop: 16, justifyContent: 'flex-end' }}>
              <button className="btn" onClick={() => setOpen(false)} disabled={pending}>Cancel</button>
              <button className="btn btn-primary" onClick={save} disabled={pending}>
                {pending ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
