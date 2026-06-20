'use client';
// Minecraft item texture with a graceful fallback (lettered tile) when the
// icon is missing or fails to load. Reused on cards, tables, and the item
// detail header.

import { useEffect, useState } from 'react';

export function ItemIcon({ icon, name, size = 36 }: { icon: string | null; name: string; size?: number }) {
  const [failed, setFailed] = useState(false);
  // Reset the fallback when the icon changes (React reuses the instance across
  // list re-renders, so a 404 would otherwise stick to a different item).
  useEffect(() => {
    setFailed(false);
  }, [icon]);
  const dim = { width: size, height: size };
  if (!icon || failed) {
    return (
      <span className="item-icon item-icon-fallback" style={{ ...dim, fontSize: Math.round(size * 0.42) }} aria-hidden>
        {(name || '?').slice(0, 1).toUpperCase()}
      </span>
    );
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img className="item-icon" src={icon} alt="" style={dim} loading="lazy" onError={() => setFailed(true)} />
  );
}
