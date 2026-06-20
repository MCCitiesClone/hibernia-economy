'use client';

import { useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { adminTransferAction } from '@/lib/actions/admin-accounts';

/** Admin tool: move money between any two accounts with a memo. */
export function MoveMoneyPanel({ disabled }: { disabled: boolean }) {
  const router = useRouter();
  const [pending, start] = useTransition();
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [amount, setAmount] = useState('');
  const [memo, setMemo] = useState('');
  const [msg, setMsg] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const fromId = Number(from), toId = Number(to);
    if (!fromId || !toId) { setMsg({ kind: 'err', text: 'From and to account ids are required.' }); return; }
    start(async () => {
      const r = await adminTransferAction({ fromAccountId: fromId, toAccountId: toId, amount: amount.trim(), memo: memo.trim() });
      if (!r.ok) setMsg({ kind: 'err', text: r.error ?? 'Transfer failed.' });
      else {
        setMsg({ kind: 'ok', text: `Transferred — txn #${r.result?.txnId}.` });
        setAmount(''); setMemo('');
        router.refresh();
      }
    });
  }

  return (
    <div className="card">
      <div className="card-title">Move money between accounts</div>
      <form onSubmit={submit} style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <input className="input" value={from} onChange={(e) => setFrom(e.target.value)} placeholder="From account #" inputMode="numeric" style={{ width: 130 }} />
        <span aria-hidden>→</span>
        <input className="input" value={to} onChange={(e) => setTo(e.target.value)} placeholder="To account #" inputMode="numeric" style={{ width: 130 }} />
        <input className="input" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount" inputMode="decimal" style={{ width: 120 }} />
        <input className="input" value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="Memo" style={{ minWidth: 200 }} />
        <button className="btn" disabled={disabled || pending || !from || !to || !amount.trim()}>{pending ? 'Working…' : 'Transfer'}</button>
        {msg && <span className={msg.kind === 'ok' ? 'state-ok' : 'state-error'} style={{ fontSize: 12 }}>{msg.text}</span>}
      </form>
    </div>
  );
}
