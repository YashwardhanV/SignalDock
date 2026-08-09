import { Search } from "lucide-react";
import { useDeferredValue, useState } from "react";
import { EmptyState } from "../components/EmptyState";
import { EventComposer } from "../components/EventComposer";
import type { EventRecord } from "../types";

export function EventsView({ apiKey, events, onChanged }: { apiKey: string; events: EventRecord[]; onChanged: (message: string) => void }) {
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<EventRecord | null>(events[0] ?? null);
  const deferredQuery = useDeferredValue(query.trim().toLowerCase());
  const visible = deferredQuery ? events.filter((event) => event.eventType.toLowerCase().includes(deferredQuery)) : events;

  return (
    <div className="view-stack">
      <header className="page-heading"><div><p className="kicker">Event intake</p><h1>Events</h1><p>Idempotent records become one delivery per matched endpoint.</p></div><span className="count-chip">{events.length} retained</span></header>
      <section className="two-column">
        <article className="surface list-surface">
          <div className="search-box"><Search size={16} /><input aria-label="Filter events" onChange={(event) => setQuery(event.target.value)} placeholder="Filter by event type" value={query} /></div>
          {visible.length === 0 ? <EmptyState message="No events match this filter." /> : (
            <div className="record-list">
              {visible.map((event) => (
                <button className={selected?.id === event.id ? "record-button selected" : "record-button"} key={event.id} onClick={() => setSelected(event)} type="button">
                  <span className="event-glyph">{event.eventType[0]?.toUpperCase()}</span><div><strong>{event.eventType}</strong><span>{event.id.slice(0, 8)} · {formatDate(event.createdAt)}</span></div>
                </button>
              ))}
            </div>
          )}
        </article>
        <div className="right-stack">
          <article className="surface payload-panel">
            <div className="panel-title"><div><p className="kicker">Selected record</p><h2>{selected?.eventType ?? "No event selected"}</h2></div>{selected ? <code>{selected.id.slice(0, 8)}</code> : null}</div>
            <pre><code>{selected ? JSON.stringify(selected.payload, null, 2) : "Send an event to inspect its immutable payload."}</code></pre>
            {selected ? <dl className="meta-grid"><div><dt>Idempotency key</dt><dd>{selected.idempotencyKey}</dd></div><div><dt>Created</dt><dd>{formatDate(selected.createdAt)}</dd></div></dl> : null}
          </article>
          <article className="surface composer-panel"><EventComposer apiKey={apiKey} compact onSent={onChanged} /></article>
        </div>
      </section>
    </div>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

