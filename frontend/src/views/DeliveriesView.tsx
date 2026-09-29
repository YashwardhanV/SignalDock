import { RotateCcw, Timer } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getDelivery, retryDelivery } from "../api";
import { EmptyState } from "../components/EmptyState";
import { StatusBadge } from "../components/StatusBadge";
import type {
  Delivery,
  DeliveryAttempt,
  DeliveryDetail,
  DeliveryStatus,
} from "../types";

const FILTERS: Array<{ label: string; value: DeliveryStatus | "ALL" }> = [
  { label: "All", value: "ALL" },
  { label: "Delivered", value: "DELIVERED" },
  { label: "In flight", value: "PROCESSING" },
  { label: "Retrying", value: "RETRY_PENDING" },
  { label: "Dead", value: "DEAD" },
];

export function DeliveriesView({
  apiKey,
  deliveries,
  onChanged,
}: {
  apiKey: string;
  deliveries: Delivery[];
  onChanged: (message: string) => void;
}) {
  const [filter, setFilter] = useState<DeliveryStatus | "ALL">("ALL");
  const [selectedId, setSelectedId] = useState(deliveries[0]?.id ?? "");
  const [detail, setDetail] = useState<DeliveryDetail | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const visible = useMemo(
    () =>
      filter === "ALL"
        ? deliveries
        : deliveries.filter((item) => item.status === filter),
    [deliveries, filter],
  );

  useEffect(() => {
    if (!selectedId) {
      setDetail(null);
      return;
    }
    let cancelled = false;
    void getDelivery(apiKey, selectedId)
      .then((response) => {
        if (!cancelled) setDetail(response);
      })
      .catch((caught) => {
        if (!cancelled)
          setError(
            caught instanceof Error
              ? caught.message
              : "Could not load delivery.",
          );
      });
    return () => {
      cancelled = true;
    };
  }, [apiKey, selectedId, deliveries]);

  async function retry() {
    if (!detail) return;
    setBusy(true);
    setError("");
    try {
      await retryDelivery(apiKey, detail.delivery.id);
      onChanged("Dead delivery requeued with a fresh attempt budget.");
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : "Could not retry delivery.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="view-stack">
      <header className="page-heading">
        <div>
          <p className="kicker">Attempt ledger</p>
          <h1>Deliveries</h1>
          <p>
            Inspect state transitions, HTTP outcomes, latency, and retry timing.
          </p>
        </div>
        <span className="count-chip">{deliveries.length} recent</span>
      </header>
      {error ? (
        <p className="banner error" role="alert">
          {error}
        </p>
      ) : null}
      <div
        className="filter-tabs"
        role="tablist"
        aria-label="Delivery status filter"
      >
        {FILTERS.map((item) => (
          <button
            aria-selected={filter === item.value}
            className={filter === item.value ? "active" : ""}
            key={item.value}
            onClick={() => setFilter(item.value)}
            role="tab"
            type="button"
          >
            {item.label}
          </button>
        ))}
      </div>
      <section className="delivery-layout">
        <article className="surface delivery-index">
          {visible.length === 0 ? (
            <EmptyState message="No deliveries in this state." />
          ) : (
            visible.map((delivery) => (
              <DeliveryIndexRow
                delivery={delivery}
                key={delivery.id}
                onSelect={() => setSelectedId(delivery.id)}
                selected={selectedId === delivery.id}
              />
            ))
          )}
        </article>
        <article className="surface detail-panel">
          {!detail ? (
            <EmptyState message="Choose a delivery to inspect attempts." />
          ) : (
            <>
              <div className="detail-head">
                <div>
                  <p className="kicker">
                    Delivery {detail.delivery.id.slice(0, 8)}
                  </p>
                  <h2>{detail.delivery.eventType}</h2>
                  <span>{detail.delivery.endpointUrl}</span>
                </div>
                <StatusBadge status={detail.delivery.status} />
              </div>
              <dl className="detail-metrics">
                <div>
                  <dt>Attempts</dt>
                  <dd>
                    {detail.delivery.attemptCount}/{detail.delivery.maxAttempts}
                  </dd>
                </div>
                <div>
                  <dt>Last update</dt>
                  <dd>{formatDate(detail.delivery.updatedAt)}</dd>
                </div>
                <div>
                  <dt>Next try</dt>
                  <dd>
                    {detail.delivery.status === "RETRY_PENDING"
                      ? formatDate(detail.delivery.nextRetryAt)
                      : "—"}
                  </dd>
                </div>
              </dl>
              {detail.delivery.lastError ? (
                <p className="failure-callout">{detail.delivery.lastError}</p>
              ) : null}
              {detail.delivery.status === "DEAD" ? (
                <button
                  className="button button-coral"
                  disabled={busy}
                  onClick={() => void retry()}
                  type="button"
                >
                  <RotateCcw size={16} />
                  Retry delivery
                </button>
              ) : null}
              <div className="timeline">
                <h3>Attempt history</h3>
                {detail.attempts.length === 0 ? (
                  <EmptyState message="Worker has not attempted this delivery yet." />
                ) : (
                  detail.attempts.map((attempt) => (
                    <AttemptItem attempt={attempt} key={attempt.id} />
                  ))
                )}
              </div>
            </>
          )}
        </article>
      </section>
    </div>
  );
}

function DeliveryIndexRow({
  delivery,
  onSelect,
  selected,
}: {
  delivery: Delivery;
  onSelect: () => void;
  selected: boolean;
}) {
  return (
    <button
      className={
        selected ? "delivery-index-row selected" : "delivery-index-row"
      }
      onClick={onSelect}
      type="button"
    >
      <span className="event-glyph">
        {delivery.eventType[0]?.toUpperCase()}
      </span>
      <div>
        <strong>{delivery.eventType}</strong>
        <span>{delivery.endpointName}</span>
        <small>{formatDate(delivery.createdAt)}</small>
      </div>
      <StatusBadge status={delivery.status} />
    </button>
  );
}

function AttemptItem({ attempt }: { attempt: DeliveryAttempt }) {
  const succeeded = Boolean(attempt.httpStatus && attempt.httpStatus < 300);
  return (
    <article>
      <span
        className={succeeded ? "timeline-dot success" : "timeline-dot failed"}
      />
      <div>
        <strong>Attempt {attempt.attemptNumber}</strong>
        <span>
          {attempt.httpStatus
            ? `HTTP ${attempt.httpStatus}`
            : attempt.errorMessage}
        </span>
        {attempt.responseBody ? <code>{attempt.responseBody}</code> : null}
      </div>
      <small>
        <Timer size={13} />
        {attempt.latencyMs} ms
      </small>
    </article>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
