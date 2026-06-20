// Server-renderable candlestick chart. Pure SVG, no recharts. Each candle is
// a thin wick + a body rectangle; green when close ≥ open, red otherwise.

import type { Candle } from '@/lib/derived';
import { CURRENCY_SYMBOL } from '@/lib/currency';

interface CandlestickProps {
  candles: Candle[];
  height?: number;
}

export function Candlestick({ candles, height = 240 }: CandlestickProps) {
  if (candles.length === 0) {
    return (
      <div className="state-empty" style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        No trades in this window.
      </div>
    );
  }

  const W = 900;
  const H = height;
  const padL = 56;
  const padR = 16;
  const padT = 12;
  const padB = 28;
  const chartW = W - padL - padR;
  const chartH = H - padT - padB;

  let min = Math.min(...candles.map((c) => c.low));
  let max = Math.max(...candles.map((c) => c.high));
  // Flat window (all prices equal): pad the domain symmetrically so the candles
  // sit mid-chart as a readable line instead of collapsing onto the baseline.
  if (max - min < 1e-9) {
    const pad = Math.max(0.5, Math.abs(max) * 0.04);
    min -= pad;
    max += pad;
  }
  const span = max - min;

  function y(price: number) {
    return padT + chartH - ((price - min) / span) * chartH;
  }
  const slot = chartW / candles.length;
  const bodyW = Math.max(2, slot * 0.7);

  // 5 horizontal gridlines
  const gridY = Array.from({ length: 5 }, (_, i) => min + (i / 4) * span);

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" height={H} role="img" aria-label="Candlestick">
      {gridY.map((g) => (
        <g key={g}>
          <line x1={padL} y1={y(g)} x2={W - padR} y2={y(g)} stroke="var(--border-soft)" strokeDasharray="2 4" />
          <text x={padL - 6} y={y(g) + 3} fontSize={10} fill="var(--fg-muted)" textAnchor="end">
            {CURRENCY_SYMBOL}{g.toFixed(2)}
          </text>
        </g>
      ))}
      {candles.map((c, i) => {
        const cx = padL + slot * (i + 0.5);
        const up = c.close >= c.open;
        const color = up ? 'var(--good)' : 'var(--bad)';
        const yOpen = y(c.open);
        const yClose = y(c.close);
        const yHigh = y(c.high);
        const yLow = y(c.low);
        const top = Math.min(yOpen, yClose);
        // Keep flat candles (open ≈ close) visible as a thin body, not a hairline.
        const h = Math.max(2, Math.abs(yOpen - yClose));
        return (
          <g key={i}>
            <line x1={cx} y1={yHigh} x2={cx} y2={yLow} stroke={color} strokeWidth={1} />
            <rect x={cx - bodyW / 2} y={top} width={bodyW} height={h} fill={color} fillOpacity={up ? 0.7 : 0.85} />
            <title>{`${c.bucketStart.toISOString().slice(0, 16).replace('T', ' ')}\nO ${c.open.toFixed(2)} · H ${c.high.toFixed(2)} · L ${c.low.toFixed(2)} · C ${c.close.toFixed(2)}\nVolume ${c.volume}`}</title>
          </g>
        );
      })}
    </svg>
  );
}
