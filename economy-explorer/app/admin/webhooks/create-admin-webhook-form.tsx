'use client';
import { useState, useTransition } from 'react';
import { adminCreateWebhookAction } from '@/lib/actions/webhooks';
import { isDiscordWebhookUrl } from '@/lib/util/discord';

export function CreateAdminWebhookForm() {
  const [pending, start] = useTransition();
  const [accountId, setAccountId] = useState('');
  const [url, setUrl] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [secret, setSecret] = useState<string | null>(null);

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setErr(null);
    setSecret(null);
    const id = Number(accountId);
    if (!Number.isInteger(id) || id <= 0) {
      setErr('Enter a valid account id.');
      return;
    }
    start(async () => {
      const r = await adminCreateWebhookAction({ accountId: id, url });
      if (!r.ok) setErr(r.error ?? 'Failed');
      else {
        setUrl('');
        setAccountId('');
        setSecret(r.secret ?? null);
      }
    });
  }

  return (
    <>
      <form onSubmit={onSubmit} style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <input
          className="input"
          type="number"
          min="1"
          placeholder="Account #"
          value={accountId}
          onChange={(e) => setAccountId(e.target.value)}
          required
          style={{ width: 120 }}
        />
        <input
          className="input"
          type="url"
          placeholder="https://endpoint.example.com/hook"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          required
          style={{ minWidth: 320, flex: 1 }}
        />
        <button className="btn" disabled={pending || !accountId || !url.trim()}>{pending ? 'Creating…' : 'Create'}</button>
        {err && <span className="state-error" style={{ fontSize: 11 }}>{err}</span>}
      </form>
      {isDiscordWebhookUrl(url) && (
        <p className="muted small" style={{ marginTop: 8, marginBottom: 0 }}>
          ✨ Looks like a Discord webhook — deliveries to this URL are sent as a rich embed.
        </p>
      )}
      {secret && (
        <div className="card" style={{ marginTop: 10 }}>
          <div className="card-title">Signing secret <span className="sub">shown once — copy it now</span></div>
          <code className="mono" style={{ display: 'block', wordBreak: 'break-all', userSelect: 'all' }}>{secret}</code>
        </div>
      )}
    </>
  );
}
