// Day-window selector chips for the dashboard pages (Health, Government,
// Money-flow, Market). Plain anchors — a window change is a full SSR reload.
export function WindowTabs({
  basePath,
  days,
  windows = [7, 30],
}: {
  basePath: string;
  days: number;
  windows?: number[];
}) {
  return (
    <span className="window-tabs">
      {windows.map((w) => (
        <a key={w} href={`${basePath}?days=${w}`} className={w === days ? 'active' : ''}>
          {w}d
        </a>
      ))}
    </span>
  );
}
