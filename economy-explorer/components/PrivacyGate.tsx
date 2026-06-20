// Inline gate shown in place of private content for visitors without the right
// access. Mirrors the SPA's treasury-ui/src/explorer/PrivacyGate.tsx.
import Link from 'next/link';
import { signIn } from '@/lib/auth/authjs';

interface PrivacyGateProps {
  kind: 'login' | 'private' | 'link';
  title: string;
  hint?: string;
}

function LockIcon() {
  return (
    <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <rect x="4.5" y="10.5" width="15" height="10" rx="2.5" />
      <path d="M8 10.5V7a4 4 0 0 1 8 0v3.5" />
    </svg>
  );
}

function LinkIcon() {
  return (
    <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M9 13a5 5 0 0 0 7 0l2-2a5 5 0 0 0-7-7l-1 1" />
      <path d="M15 11a5 5 0 0 0-7 0l-2 2a5 5 0 0 0 7 7l1-1" />
    </svg>
  );
}

export function PrivacyGate({ kind, title, hint }: PrivacyGateProps) {
  return (
    <div className={`privacy-gate privacy-gate--card privacy-gate-${kind}`}>
      <div className="privacy-gate-icon">{kind === 'link' ? <LinkIcon /> : <LockIcon />}</div>
      <div className="privacy-gate-title">{title}</div>
      {hint && <div className="privacy-gate-hint">{hint}</div>}
      {kind === 'login' && (
        <div className="privacy-gate-actions">
          <form
            action={async () => {
              'use server';
              await signIn('keycloak');
            }}
          >
            <button type="submit" className="btn btn-primary">Log in</button>
          </form>
        </div>
      )}
      {kind === 'link' && (
        <div className="privacy-gate-actions">
          <Link href="/link" className="btn btn-primary" prefetch={false}>Link your account</Link>
        </div>
      )}
    </div>
  );
}
