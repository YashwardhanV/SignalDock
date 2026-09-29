import type {
  DashboardSummary,
  Delivery,
  DeliveryDetail,
  Endpoint,
  EventRecord,
  Page,
  Snapshot,
  Subscription,
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(
  apiKey: string,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        "X-API-Key": apiKey,
        ...init.headers,
      },
    });
  } catch {
    throw new ApiError("SignalDock API is not reachable yet.", 0);
  }

  if (!response.ok) {
    const fallback = `${response.status} ${response.statusText}`;
    try {
      const body = (await response.json()) as {
        message?: string;
        fieldErrors?: Record<string, string>;
      };
      const fieldMessage = body.fieldErrors
        ? Object.entries(body.fieldErrors)
            .map(([field, message]) => `${field}: ${message}`)
            .join(", ")
        : "";
      throw new ApiError(
        fieldMessage || body.message || fallback,
        response.status,
      );
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(fallback, response.status);
    }
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function getSnapshot(apiKey: string): Promise<Snapshot> {
  const [summary, endpoints, subscriptions, events, deliveries] =
    await Promise.all([
      request<DashboardSummary>(apiKey, "/dashboard/summary"),
      request<Page<Endpoint>>(apiKey, "/endpoints?size=100"),
      request<Subscription[]>(apiKey, "/subscriptions"),
      request<Page<EventRecord>>(apiKey, "/events?size=100"),
      request<Page<Delivery>>(apiKey, "/deliveries?size=100"),
    ]);
  return {
    summary,
    endpoints: endpoints.content,
    subscriptions,
    events: events.content,
    deliveries: deliveries.content,
  };
}

export function ingestEvent(
  apiKey: string,
  eventType: string,
  payload: unknown,
  idempotencyKey: string,
) {
  return request<{
    eventId: string;
    deliveriesCreated: number;
    duplicate: boolean;
  }>(apiKey, "/events", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ eventType, payload }),
  });
}

export function createEndpoint(
  apiKey: string,
  input: { name: string; url: string; maxAttempts: number },
) {
  return request<{ endpoint: Endpoint; signingSecret: string }>(
    apiKey,
    "/endpoints",
    {
      method: "POST",
      body: JSON.stringify(input),
    },
  );
}

export function updateEndpoint(
  apiKey: string,
  endpoint: Endpoint,
  active: boolean,
) {
  return request<Endpoint>(apiKey, `/endpoints/${endpoint.id}`, {
    method: "PATCH",
    body: JSON.stringify({
      name: endpoint.name,
      url: endpoint.url,
      maxAttempts: endpoint.maxAttempts,
      active,
    }),
  });
}

export function createSubscription(
  apiKey: string,
  endpointId: string,
  eventPattern: string,
) {
  return request<Subscription>(
    apiKey,
    `/endpoints/${endpointId}/subscriptions`,
    {
      method: "POST",
      body: JSON.stringify({ eventPattern }),
    },
  );
}

export function deactivateSubscription(apiKey: string, subscriptionId: string) {
  return request<void>(apiKey, `/subscriptions/${subscriptionId}`, {
    method: "DELETE",
  });
}

export function getDelivery(apiKey: string, deliveryId: string) {
  return request<DeliveryDetail>(apiKey, `/deliveries/${deliveryId}`);
}

export function retryDelivery(apiKey: string, deliveryId: string) {
  return request<Delivery>(apiKey, `/deliveries/${deliveryId}/retry`, {
    method: "POST",
  });
}
