# REST API Guide

Interactive documentation is served at `/docs`; the OpenAPI document is `/api-docs`. This file records the important semantics that are easiest to discuss in review.

## Authentication and common behavior

- Base path: `/api/v1`.
- Normal endpoints require `X-API-Key`.
- `POST /admin/api-keys` requires `X-Admin-Key` instead and returns the generated raw key once.
- Clients may send `X-Request-Id`; otherwise SignalDock creates one. The response echoes it.
- JSON errors have `code`, `message`, `fieldErrors`, `requestId`, and `timestamp`.
- List responses use `{ content, page, size, totalElements, totalPages, last }`, except subscriptions, whose expected cardinality is bounded by endpoint configuration and is returned as a simple list.

## Endpoint configuration

| Method and path | Success | Purpose |
|---|---:|---|
| `POST /endpoints` | 201 | Register `{ name, url, maxAttempts }`; response includes the signing secret once. |
| `GET /endpoints?page=0&size=20` | 200 | Paginated endpoint registry with secret hints only. |
| `GET /endpoints/{id}` | 200 | Read one endpoint. |
| `PATCH /endpoints/{id}` | 200 | Replace mutable `{ name, url, maxAttempts, active }` configuration. |
| `POST /endpoints/{id}/subscriptions` | 201 | Add `{ eventPattern }`; exact, `*`, or trailing wildcard such as `order.*`. |
| `GET /subscriptions` | 200 | Read routes and endpoint labels. |
| `DELETE /subscriptions/{id}` | 204 | Deactivate a route while preserving its row/history. |

Endpoint URLs require HTTPS and a publicly resolved address by default. HTTP/private addresses exist only for the local Compose demo through explicit environment flags.

## Events

`POST /events` requires:

```http
Idempotency-Key: checkout-42
Content-Type: application/json
X-API-Key: sd_...
```

```json
{
  "eventType": "order.created",
  "payload": { "orderId": "42", "amount": 1299 }
}
```

- New key: 201, `duplicate=false`, and a `Location` header.
- Existing key: 200, same `eventId`, `duplicate=true`, and no new deliveries.
- Missing/blank/over-180-character key: 400.
- Event type: dot-separated letters, digits, `_`, or `-`, maximum 160 characters.

`GET /events?page=0&size=20` lists newest first. `GET /events/{id}` returns one event and its persisted idempotency key.

## Deliveries

| Method and path | Success | Purpose |
|---|---:|---|
| `GET /deliveries` | 200 | Newest-first page; optional `status`, `createdAfter`, `page`, and `size` (max 200). |
| `GET /deliveries/{id}` | 200 | Delivery metadata plus ordered attempt history. |
| `POST /deliveries/{id}/retry` | 200 | Move only a `DEAD` delivery to `RETRY_PENDING` and add an attempt budget. |
| `GET /dashboard/summary` | 200 | Total, counts by status, and terminal success rate. |

Manual retry on a non-`DEAD` row returns 409. Missing resources return 404. Delivery states are `PENDING`, `PROCESSING`, `RETRY_PENDING`, `DELIVERED`, and `DEAD`.

## Receiver request

SignalDock sends an HTTP POST with the exact stored JSON payload and:

```http
Content-Type: application/json
User-Agent: SignalDock/1.0
X-SignalDock-Event-Id: <uuid>
X-SignalDock-Event-Type: order.created
X-SignalDock-Delivery-Id: <uuid>
X-SignalDock-Timestamp: <unix-seconds>
X-SignalDock-Signature: sha256=<lowercase-hex-hmac>
```

Signature input is `timestamp + "." + rawPayload`, encoded as UTF-8 and signed with HMAC-SHA256. A receiver should enforce a small timestamp tolerance, recompute against the exact body bytes, compare in constant time, and deduplicate by delivery ID.

Any 2xx status is success. Other statuses, connection failures, and read timeouts are recorded as failed attempts. Response/error bodies are truncated to the configured database limit.
