'use client';
// Click-to-copy chip for the Minecraft server address in the footer.

import { useState } from 'react';

export function CopyServer({ ip }: { ip: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(ip);
      setCopied(true);
      setTimeout(() => setCopied(false), 1600);
    } catch {
      /* clipboard blocked — no-op, the text is still selectable */
    }
  }

  return (
    <button type="button" className="copy-server" onClick={copy} title="Copy server address">
      <span className="mono">{ip}</span>
      <span className="copy-server-state">{copied ? 'Copied ✓' : 'Copy'}</span>
    </button>
  );
}
