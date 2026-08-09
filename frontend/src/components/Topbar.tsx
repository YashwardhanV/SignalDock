import { KeyRound, RefreshCw } from "lucide-react";
import { FormEvent, useState } from "react";

export function Topbar({
  apiKey,
  busy,
  connected,
  onConnect,
  onRefresh,
}: {
  apiKey: string;
  busy: boolean;
  connected: boolean;
  onConnect: (key: string) => void;
  onRefresh: () => void;
}) {
  const [draft, setDraft] = useState(apiKey);

  function submit(event: FormEvent) {
    event.preventDefault();
    onConnect(draft.trim());
  }

  return (
    <header className="topbar">
      <div className="connection-state">
        <span className={connected ? "pulse-dot online" : "pulse-dot"} />
        <div><strong>{connected ? "Console connected" : "Connect your console"}</strong><span>{connected ? "Live data refreshes every 4 seconds" : "Use the demo key from README"}</span></div>
      </div>
      <form className="key-form" onSubmit={submit}>
        <KeyRound size={16} aria-hidden="true" />
        <label className="sr-only" htmlFor="api-key">API key</label>
        <input id="api-key" onChange={(event) => setDraft(event.target.value)} placeholder="X-API-Key" type="password" value={draft} />
        <button className="button button-dark" type="submit">Connect</button>
      </form>
      <button aria-label="Refresh dashboard" className="icon-button" disabled={busy || !apiKey} onClick={onRefresh} type="button">
        <RefreshCw className={busy ? "spin" : ""} size={18} />
      </button>
    </header>
  );
}

