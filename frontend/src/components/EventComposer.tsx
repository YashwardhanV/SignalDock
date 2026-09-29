import { Braces, Send } from "lucide-react";
import { FormEvent, useState } from "react";
import { ingestEvent } from "../api";

const DEFAULT_PAYLOAD = `{
  "orderId": "ord_1042",
  "total": 89.50,
  "currency": "INR"
}`;

export function EventComposer({
  apiKey,
  compact = false,
  onSent,
}: {
  apiKey: string;
  compact?: boolean;
  onSent: (message: string) => void;
}) {
  const [eventType, setEventType] = useState("order.created");
  const [idempotencyKey, setIdempotencyKey] = useState(
    () => `ui-${crypto.randomUUID()}`,
  );
  const [payloadText, setPayloadText] = useState(DEFAULT_PAYLOAD);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    let payload: unknown;
    try {
      payload = JSON.parse(payloadText);
    } catch {
      setError("Payload must be valid JSON.");
      return;
    }

    setBusy(true);
    try {
      const response = await ingestEvent(
        apiKey,
        eventType.trim(),
        payload,
        idempotencyKey.trim(),
      );
      onSent(
        response.duplicate
          ? `Duplicate ignored · event ${response.eventId.slice(0, 8)}`
          : `Event queued · ${response.deliveriesCreated} delivery${response.deliveriesCreated === 1 ? "" : "ies"}`,
      );
      setIdempotencyKey(`ui-${crypto.randomUUID()}`);
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : "Could not ingest event.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      className={compact ? "composer compact" : "composer"}
      onSubmit={submit}
    >
      <div className="section-heading">
        <span className="section-icon">
          <Braces size={17} />
        </span>
        <div>
          <h2>Send an event</h2>
          <p>Matching endpoints are queued atomically.</p>
        </div>
      </div>
      <label>
        <span>Event type</span>
        <input
          onChange={(event) => setEventType(event.target.value)}
          value={eventType}
        />
      </label>
      <label>
        <span>Idempotency key</span>
        <input
          onChange={(event) => setIdempotencyKey(event.target.value)}
          value={idempotencyKey}
        />
      </label>
      <label>
        <span>JSON payload</span>
        <textarea
          onChange={(event) => setPayloadText(event.target.value)}
          spellCheck={false}
          value={payloadText}
        />
      </label>
      {error ? (
        <p className="inline-error" role="alert">
          {error}
        </p>
      ) : null}
      <button
        className="button button-coral"
        disabled={busy || !apiKey}
        type="submit"
      >
        <Send size={16} />
        {busy ? "Queuing…" : "Queue event"}
      </button>
    </form>
  );
}
