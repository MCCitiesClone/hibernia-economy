'use client';
import { useState, useTransition } from 'react';
import { addGroupMemberAction, removeGroupMemberAction } from '@/lib/actions/admin';

interface Member {
  uuid: string;
  name: string | null;
  source: 'manual' | 'luckperms';
}

export function MemberManager({ groupId, members }: { groupId: number; members: Member[] }) {
  const [pending, start] = useTransition();
  const [identifier, setIdentifier] = useState('');
  const [err, setErr] = useState<string | null>(null);

  function onAdd(e: React.FormEvent) {
    e.preventDefault();
    setErr(null);
    start(async () => {
      const r = await addGroupMemberAction(groupId, identifier);
      if (!r.ok) setErr(r.error ?? 'Failed'); else setIdentifier('');
    });
  }

  function onRemove(uuid: string, name: string | null) {
    if (!confirm(`Remove ${name ?? uuid} from this group?`)) return;
    setErr(null);
    start(async () => {
      const r = await removeGroupMemberAction(groupId, uuid);
      if (!r.ok) setErr(r.error ?? 'Failed');
    });
  }

  return (
    <div>
      <form onSubmit={onAdd} style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 10 }}>
        <input
          className="input"
          placeholder="Player name or UUID"
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          style={{ minWidth: 260 }}
        />
        <button className="btn" disabled={pending || !identifier.trim()}>{pending ? 'Adding…' : 'Add member'}</button>
        {err && <span className="state-error" style={{ fontSize: 11 }}>{err}</span>}
      </form>

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr><th>Player</th><th>Source</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {members.length === 0 && <tr><td colSpan={3} className="empty">No members.</td></tr>}
            {members.map((m) => (
              <tr key={m.uuid}>
                <td>{m.name ?? <span className="mono">{m.uuid}</span>}</td>
                <td>
                  {m.source === 'luckperms'
                    ? <span className="badge">synced</span>
                    : <span className="badge badge-active">manual</span>}
                </td>
                <td>
                  {m.source === 'manual'
                    ? <button className="btn" disabled={pending} onClick={() => onRemove(m.uuid, m.name)}>Remove</button>
                    : <span className="sub">managed by cron</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
