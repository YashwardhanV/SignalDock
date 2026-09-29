import { Cable, Copy, Pause, Play, Plus, Route } from "lucide-react";
import { FormEvent, useState } from "react";
import { createEndpoint, createSubscription, deactivateSubscription, updateEndpoint } from "../api";
import { EmptyState } from "../components/EmptyState";
import type { Endpoint, Subscription } from "../types";

export function EndpointsView({ apiKey, endpoints, subscriptions, onChanged }: { apiKey: string; endpoints: Endpoint[]; subscriptions: Subscription[]; onChanged: (message: string) => void }) {
  const [name, setName] = useState("Orders receiver");
  const [url, setUrl] = useState("https://example.com/callbacks/orders");
  const [maxAttempts, setMaxAttempts] = useState(5);
  const [endpointId, setEndpointId] = useState(endpoints[0]?.id ?? "");
  const [pattern, setPattern] = useState("order.*");
  const [secret, setSecret] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const selectedEndpointId = endpointId || endpoints[0]?.id || "";

  async function addEndpoint(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError("");
    try {
      const created = await createEndpoint(apiKey, { name: name.trim(), url: url.trim(), maxAttempts });
      setSecret(created.signingSecret); setEndpointId(created.endpoint.id); onChanged("Endpoint registered. Copy its signing secret now.");
    } catch (caught) { setError(caught instanceof Error ? caught.message : "Could not register endpoint."); }
    finally { setBusy(false); }
  }

  async function addSubscription(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError("");
    try { await createSubscription(apiKey, selectedEndpointId, pattern.trim()); onChanged("Event route created."); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Could not create route."); }
    finally { setBusy(false); }
  }

  async function toggle(endpoint: Endpoint) {
    setBusy(true); setError("");
    try { await updateEndpoint(apiKey, endpoint, !endpoint.active); onChanged(endpoint.active ? "Endpoint paused." : "Endpoint activated."); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Could not update endpoint."); }
    finally { setBusy(false); }
  }

  async function removeRoute(subscription: Subscription) {
    setBusy(true); setError("");
    try { await deactivateSubscription(apiKey, subscription.id); onChanged("Event route deactivated."); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Could not remove route."); }
    finally { setBusy(false); }
  }

  return (
    <div className="view-stack">
      <header className="page-heading"><div><p className="kicker">Destination registry</p><h1>Endpoints & routes</h1><p>Secrets are shown once; event patterns decide which callbacks are queued.</p></div><span className="count-chip">{endpoints.filter((item) => item.active).length} active</span></header>
      {error ? <p className="banner error" role="alert">{error}</p> : null}
      {secret ? <div className="secret-banner"><div><strong>Copy the signing secret now</strong><span>Only a redacted hint is returned later.</span></div><code>{secret}</code><button aria-label="Copy signing secret" onClick={() => void navigator.clipboard.writeText(secret)} type="button"><Copy size={17} /></button></div> : null}
      <section className="endpoint-layout">
        <article className="surface endpoint-registry">
          <div className="panel-title"><div><p className="kicker">Registered</p><h2>Callback endpoints</h2></div><Cable size={20} /></div>
          {endpoints.length === 0 ? <EmptyState message="Register the first endpoint." /> : endpoints.map((endpoint) => (
            <article className="endpoint-card" key={endpoint.id}>
              <span className={endpoint.active ? "endpoint-state active" : "endpoint-state"} />
              <div><strong>{endpoint.name}</strong><span>{endpoint.url}</span><small>{endpoint.secretHint} · {endpoint.maxAttempts} attempts</small></div>
              <button className="mini-button" disabled={busy} onClick={() => void toggle(endpoint)} type="button">{endpoint.active ? <Pause size={14} /> : <Play size={14} />}{endpoint.active ? "Pause" : "Activate"}</button>
            </article>
          ))}
        </article>
        <div className="right-stack">
          <article className="surface form-surface">
            <div className="section-heading"><span className="section-icon"><Plus size={17} /></span><div><h2>Register endpoint</h2><p>Must be an http or https URL.</p></div></div>
            <form className="stack-form" onSubmit={addEndpoint}>
              <label><span>Name</span><input onChange={(event) => setName(event.target.value)} value={name} /></label>
              <label><span>Callback URL</span><input onChange={(event) => setUrl(event.target.value)} value={url} /></label>
              <label><span>Maximum attempts</span><input max={12} min={1} onChange={(event) => setMaxAttempts(Number(event.target.value))} type="number" value={maxAttempts} /></label>
              <button className="button button-coral" disabled={busy} type="submit">Register endpoint</button>
            </form>
          </article>
          <article className="surface form-surface">
            <div className="section-heading"><span className="section-icon"><Route size={17} /></span><div><h2>Add event route</h2><p>Exact, order.* or * patterns.</p></div></div>
            <form className="stack-form" onSubmit={addSubscription}>
              <label><span>Endpoint</span><select onChange={(event) => setEndpointId(event.target.value)} value={selectedEndpointId}><option value="">Choose endpoint</option>{endpoints.filter((item) => item.active).map((endpoint) => <option key={endpoint.id} value={endpoint.id}>{endpoint.name}</option>)}</select></label>
              <label><span>Event pattern</span><input onChange={(event) => setPattern(event.target.value)} value={pattern} /></label>
              <button className="button button-dark" disabled={busy || !selectedEndpointId} type="submit">Create route</button>
            </form>
            <div className="route-list">{subscriptions.filter((item) => item.active).map((subscription) => <button key={subscription.id} onClick={() => void removeRoute(subscription)} title="Deactivate route" type="button"><span>{subscription.eventPattern}</span><small>→ {subscription.endpointName}</small></button>)}</div>
          </article>
        </div>
      </section>
    </div>
  );
}
