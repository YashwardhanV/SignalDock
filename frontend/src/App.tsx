import { useCallback, useEffect, useState } from "react";
import { getSnapshot } from "./api";
import { Sidebar, type View } from "./components/Sidebar";
import { Topbar } from "./components/Topbar";
import type { Snapshot } from "./types";
import { DeliveriesView } from "./views/DeliveriesView";
import { EndpointsView } from "./views/EndpointsView";
import { EventsView } from "./views/EventsView";
import { OverviewView } from "./views/OverviewView";

const STORAGE_KEY = "signaldock:v1:api-key";
const EMPTY_SNAPSHOT: Snapshot = {
  summary: { total: 0, byStatus: { PENDING: 0, PROCESSING: 0, RETRY_PENDING: 0, DELIVERED: 0, DEAD: 0 }, successRate: 0 },
  endpoints: [], subscriptions: [], events: [], deliveries: [],
};

export default function App() {
  const [view, setView] = useState<View>("overview");
  const [apiKey, setApiKey] = useState(() => localStorage.getItem(STORAGE_KEY) ?? "");
  const [data, setData] = useState<Snapshot>(EMPTY_SNAPSHOT);
  const [busy, setBusy] = useState(false);
  const [connected, setConnected] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const refresh = useCallback(async (showBusy = true) => {
    if (!apiKey) return;
    if (showBusy) setBusy(true);
    try {
      const snapshot = await getSnapshot(apiKey);
      setData(snapshot); setConnected(true); setError("");
    } catch (caught) {
      setConnected(false); setError(caught instanceof Error ? caught.message : "Could not refresh dashboard.");
    } finally {
      if (showBusy) setBusy(false);
    }
  }, [apiKey]);

  useEffect(() => { if (apiKey) void refresh(); }, [apiKey, refresh]);
  useEffect(() => {
    if (!apiKey) return undefined;
    const id = window.setInterval(() => void refresh(false), 4000);
    return () => window.clearInterval(id);
  }, [apiKey, refresh]);
  useEffect(() => {
    if (!message) return undefined;
    const id = window.setTimeout(() => setMessage(""), 3500);
    return () => window.clearTimeout(id);
  }, [message]);

  function connect(key: string) {
    localStorage.setItem(STORAGE_KEY, key);
    setApiKey(key); setConnected(false);
  }

  function changed(nextMessage: string) {
    setMessage(nextMessage);
    window.setTimeout(() => void refresh(false), 250);
  }

  return (
    <div className="shell">
      <Sidebar active={view} onNavigate={setView} />
      <div className="workspace-shell">
        <Topbar apiKey={apiKey} busy={busy} connected={connected} onConnect={connect} onRefresh={() => void refresh()} />
        {error ? <p className="banner error" role="alert">{error}</p> : null}
        {message ? <p className="toast" role="status">{message}</p> : null}
        <main className="content">
          {view === "overview" ? <OverviewView apiKey={apiKey} data={data} onChanged={changed} onOpenDeliveries={() => setView("deliveries")} /> : null}
          {view === "events" ? <EventsView apiKey={apiKey} events={data.events} onChanged={changed} /> : null}
          {view === "endpoints" ? <EndpointsView apiKey={apiKey} endpoints={data.endpoints} subscriptions={data.subscriptions} onChanged={changed} /> : null}
          {view === "deliveries" ? <DeliveriesView apiKey={apiKey} deliveries={data.deliveries} onChanged={changed} /> : null}
        </main>
        <footer className="portfolio-footer">
          <span>Built and maintained by Yashwardhan Verma</span>
          <a href="https://github.com/YashwardhanV" rel="noreferrer" target="_blank">GitHub</a>
          <a href="https://www.linkedin.com/in/yashwardhanv" rel="noreferrer" target="_blank">LinkedIn</a>
          <a href="mailto:yashwardhanverma108@gmail.com">Email</a>
        </footer>
      </div>
    </div>
  );
}
