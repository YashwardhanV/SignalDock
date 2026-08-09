import { Activity, ArrowUpRight, CheckCircle2, Clock3, Skull, Waypoints } from "lucide-react";
import { EventComposer } from "../components/EventComposer";
import { EmptyState } from "../components/EmptyState";
import { StatusBadge } from "../components/StatusBadge";
import type { Snapshot } from "../types";

export function OverviewView({ apiKey, data, onChanged, onOpenDeliveries }: { apiKey: string; data: Snapshot; onChanged: (message: string) => void; onOpenDeliveries: () => void }) {
  const delivered = data.summary.byStatus.DELIVERED ?? 0;
  const pending = (data.summary.byStatus.PENDING ?? 0) + (data.summary.byStatus.PROCESSING ?? 0) + (data.summary.byStatus.RETRY_PENDING ?? 0);
  const dead = data.summary.byStatus.DEAD ?? 0;
  const terminal = delivered + dead;
  const deliveredWidth = terminal === 0 ? 0 : (delivered / terminal) * 100;

  const cards = [
    { label: "Delivered", value: delivered, hint: `${data.summary.successRate.toFixed(1)}% terminal success`, icon: CheckCircle2, tone: "mint" },
    { label: "In flight", value: pending, hint: "Pending, sending & retrying", icon: Clock3, tone: "amber" },
    { label: "Dead", value: dead, hint: "Eligible for manual replay", icon: Skull, tone: "coral" },
    { label: "Endpoints", value: data.endpoints.filter((item) => item.active).length, hint: `${data.subscriptions.filter((item) => item.active).length} active routes`, icon: Waypoints, tone: "blue" },
  ];

  return (
    <div className="view-stack">
      <section className="hero-row">
        <div><p className="kicker">Operations / today</p><h1>Every callback,<br /><em>accounted for.</em></h1><p className="hero-copy">Ingest once, sign every request, retry failures, and retain the complete attempt trail.</p></div>
        <div className="health-card">
          <div className="health-head"><span><Activity size={17} /> Delivery health</span><strong>{data.summary.successRate.toFixed(1)}%</strong></div>
          <div className="health-track"><span style={{ width: `${deliveredWidth}%` }} /></div>
          <div className="health-legend"><span><i className="mint-dot" />{delivered} delivered</span><span><i className="coral-dot" />{dead} dead</span></div>
        </div>
      </section>

      <section className="metric-grid">
        {cards.map(({ label, value, hint, icon: Icon, tone }) => (
          <article className="metric-card" key={label}>
            <span className={`metric-icon ${tone}`}><Icon size={19} /></span><span>{label}</span><strong>{value}</strong><small>{hint}</small>
          </article>
        ))}
      </section>

      <section className="overview-grid">
        <article className="surface recent-panel">
          <div className="panel-title"><div><p className="kicker">Live ledger</p><h2>Recent deliveries</h2></div><button className="text-button" onClick={onOpenDeliveries} type="button">View all <ArrowUpRight size={15} /></button></div>
          {data.deliveries.length === 0 ? <EmptyState message="No deliveries yet. Send the first event." /> : (
            <div className="delivery-list">
              {data.deliveries.slice(0, 7).map((delivery) => (
                <article className="delivery-row" key={delivery.id}>
                  <span className="event-glyph">{delivery.eventType.slice(0, 1).toUpperCase()}</span>
                  <div><strong>{delivery.eventType}</strong><span>{delivery.endpointName} · {shortTime(delivery.createdAt)}</span></div>
                  <span className="attempt-count">{delivery.attemptCount}/{delivery.maxAttempts}</span>
                  <StatusBadge status={delivery.status} />
                </article>
              ))}
            </div>
          )}
        </article>
        <article className="surface composer-panel"><EventComposer apiKey={apiKey} compact onSent={onChanged} /></article>
      </section>
    </div>
  );
}

function shortTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", month: "short", day: "numeric" }).format(new Date(value));
}

