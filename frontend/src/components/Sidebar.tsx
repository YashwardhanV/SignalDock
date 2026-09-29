import { Boxes, Cable, Gauge, RadioTower, Send } from "lucide-react";

export type View = "overview" | "events" | "endpoints" | "deliveries";

const ITEMS = [
  { id: "overview" as const, label: "Overview", icon: Gauge },
  { id: "events" as const, label: "Events", icon: Send },
  { id: "endpoints" as const, label: "Endpoints", icon: Cable },
  { id: "deliveries" as const, label: "Deliveries", icon: Boxes },
];

export function Sidebar({
  active,
  onNavigate,
}: {
  active: View;
  onNavigate: (view: View) => void;
}) {
  return (
    <aside className="sidebar">
      <div className="brand-lockup">
        <span className="brand-mark" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
        <div>
          <strong>SignalDock</strong>
          <span>Delivery console</span>
        </div>
      </div>
      <nav aria-label="Primary navigation">
        {ITEMS.map(({ id, label, icon: Icon }) => (
          <button
            className={active === id ? "nav-item active" : "nav-item"}
            key={id}
            onClick={() => onNavigate(id)}
            type="button"
          >
            <Icon size={18} aria-hidden="true" />
            <span>{label}</span>
          </button>
        ))}
      </nav>
      <div className="sidebar-note">
        <RadioTower size={18} aria-hidden="true" />
        <div>
          <strong>PostgreSQL queue</strong>
          <span>No broker required</span>
        </div>
      </div>
    </aside>
  );
}
