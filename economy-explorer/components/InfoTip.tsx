// Small inline "?" badge with a hover tooltip. Server-renderable; uses
// the native <details>/<summary> pattern but styled to look like a chip
// (we want the popover behaviour without client JS).

export function InfoTip({ children }: { children: React.ReactNode }) {
  return (
    <span className="info-tip" tabIndex={0}>
      <span className="info-tip-icon" aria-hidden>?</span>
      <span className="info-tip-body" role="tooltip">{children}</span>
    </span>
  );
}
