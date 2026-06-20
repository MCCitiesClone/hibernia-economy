import { buildMetadata } from '@/lib/metadata';
import Link from 'next/link';
import type { Route } from 'next';
import { notFound } from 'next/navigation';
import { getMarketFirm, listFirmMarketItems } from '@/lib/sql/market';
import { fmtAmtFull, fmtN } from '@/lib/format';
import { BackLink } from '@/components/BackLink';
import { SectionTabs } from '@/components/SectionTabs';
import { CsvButton } from '@/components/CsvButton';
import { JsonButton } from '@/components/JsonButton';
import { PrivacyGate } from '@/components/PrivacyGate';
import { getViewer } from '@/lib/auth/viewer';
import { canViewFirmFinancials, isStaff } from '@/lib/auth/access';
import { hasFirmFinancialAccess } from '@/lib/sql/firm';
import { auditView } from '@/lib/audit';

export const dynamic = 'force-dynamic';

export async function generateMetadata({ params }: { params: Promise<{ firmId: string }> }) {
  const { firmId } = await params;
  return buildMetadata({ title: `Firm #${firmId}`, description: 'ChestShop sales and shops for this {server} firm.', path: `/chestshop/firms/${firmId}` });
}

export default async function MarketFirmDetailPage({
  params,
}: {
  params: Promise<{ firmId: string }>;
}) {
  const { firmId: raw } = await params;
  const firmId = Number(raw);
  if (!Number.isInteger(firmId) || firmId <= 0) notFound();

  const viewer = await getViewer();
  const canSeeFinancials = await canViewFirmFinancials(firmId, viewer);

  // Resolve identity for the heading + existence check regardless of access;
  // the financial figures (KPIs + per-item volume) only render when allowed.
  const firm = await getMarketFirm(firmId);
  if (!firm) notFound();
  const items = canSeeFinancials ? await listFirmMarketItems(firmId, 50, 0) : [];

  // Audit privileged inspection: staff viewing a firm's books via elevation
  // (i.e. not one of that firm's own finance-role employees).
  if (canSeeFinancials && isStaff(viewer)) {
    const financeEmployee = viewer.minecraftUuid
      ? await hasFirmFinancialAccess(firmId, viewer.minecraftUuid)
      : false;
    if (!financeEmployee) {
      await auditView(viewer, { path: `/chestshop/firms/${firmId}`, targetType: 'firm', targetId: String(firmId) });
    }
  }

  return (
    <>
      <BackLink href="/chestshop/firms" label="Market firms" />

      <div className="page-heading">
        <h1>{firm.display_name ?? `Firm #${firm.firm_id}`}</h1>
        <span className="mono" style={{ color: 'var(--fg-muted)', fontSize: 13 }}>#{firm.firm_id}</span>
        <SectionTabs />
      </div>

      {!canSeeFinancials ? (
        <PrivacyGate
          kind={viewer.anon ? 'login' : 'private'}
          title="This firm's financials are private"
          hint="Sales, volume, and quantities are only visible to the firm's finance-role employees and to staff. Balances are public; profitability is not."
        />
      ) : (
        <>
          <div className="kpi-grid">
            <div className="kpi">
              <div className="kpi-label">Sales</div>
              <div className="kpi-value">{fmtN(firm.sale_count)}</div>
            </div>
            <div className="kpi">
              <div className="kpi-label">Quantity moved</div>
              <div className="kpi-value">{fmtN(firm.total_quantity)}</div>
            </div>
            <div className="kpi">
              <div className="kpi-label">Volume</div>
              <div className="kpi-value">{fmtAmtFull(firm.total_volume)}</div>
            </div>
            <div className="kpi">
              <div className="kpi-label">Distinct items</div>
              <div className="kpi-value">{fmtN(items.length)}</div>
            </div>
          </div>

          <div className="card">
            <div className="card-title">
              Items sold <span className="sub">all-time, sorted by trade count</span>
              <span className="toolbar-export" style={{ marginLeft: 'auto' }}>
                <CsvButton
                  filename={`firm-${firm.firm_id}-items.csv`}
                  headers={['Item key', 'Item name', 'Material', 'Trades', 'Quantity', 'Volume']}
                  rows={items.map((i) => [
                    i.item_key,
                    i.item_name ?? '',
                    i.material ?? '',
                    i.trade_count,
                    i.total_quantity,
                    i.total_volume,
                  ])}
                />
                <JsonButton filename={`firm-${firm.firm_id}-items.json`} data={items} />
              </span>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Material</th>
                    <th className="amount">Trades</th>
                    <th className="amount">Quantity</th>
                    <th className="amount">Volume</th>
                  </tr>
                </thead>
                <tbody>
                  {items.length === 0 && (
                    <tr><td colSpan={5} className="empty">No items.</td></tr>
                  )}
                  {items.map((i) => (
                    <tr key={i.item_key}>
                      <td>
                        <Link href={`/chestshop/items/${encodeURIComponent(i.item_key)}` as Route} className="rowlink" prefetch={false}>
                          <span style={{ fontWeight: 500 }}>{i.item_name ?? i.item_key}</span>
                        </Link>
                      </td>
                      <td><span className="mono small" style={{ color: 'var(--fg-muted)' }}>{i.material ?? '—'}</span></td>
                      <td className="amount mono">{fmtN(i.trade_count)}</td>
                      <td className="amount mono">{fmtN(i.total_quantity)}</td>
                      <td className="amount neutral">{fmtAmtFull(i.total_volume)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </>
  );
}
