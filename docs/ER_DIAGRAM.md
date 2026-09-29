# Database and ER Diagram

```mermaid
erDiagram
    WEBHOOK_ENDPOINTS {
        uuid id PK
        varchar name
        varchar url
        varchar signing_secret
        boolean active
        int max_attempts
        timestamptz created_at
        timestamptz updated_at
    }

    ENDPOINT_SUBSCRIPTIONS {
        uuid id PK
        uuid endpoint_id FK
        varchar event_pattern
        boolean active
        timestamptz created_at
    }

    EVENTS {
        uuid id PK
        varchar event_type
        jsonb payload
        varchar idempotency_key UK
        timestamptz created_at
    }

    DELIVERIES {
        uuid id PK
        uuid event_id FK
        uuid endpoint_id FK
        varchar status
        int attempt_count
        int max_attempts
        timestamptz next_retry_at
        timestamptz lease_until
        text last_error
        timestamptz completed_at
        timestamptz created_at
        timestamptz updated_at
    }

    DELIVERY_ATTEMPTS {
        uuid id PK
        uuid delivery_id FK
        int attempt_number
        timestamptz started_at
        timestamptz finished_at
        int http_status
        varchar response_body
        varchar error_message
        bigint latency_ms
    }

    WEBHOOK_ENDPOINTS ||--o{ ENDPOINT_SUBSCRIPTIONS : has
    EVENTS ||--o{ DELIVERIES : creates
    WEBHOOK_ENDPOINTS ||--o{ DELIVERIES : receives
    DELIVERIES ||--o{ DELIVERY_ATTEMPTS : records
```

## Constraints

- `events.idempotency_key` is unique, so concurrent retries of one request produce one event.
- `(event_id, endpoint_id)` is unique on `deliveries`: two matching patterns cannot create duplicate work for one receiver.
- `(endpoint_id, event_pattern)` is unique on `endpoint_subscriptions`. Patterns are stored in lower case.
- `(delivery_id, attempt_number)` is unique on `delivery_attempts`, which keeps the attempt sequence auditable.
- Check constraints limit `deliveries.status` to the five states, `max_attempts` to 1–12 on endpoints, and keep counters and latency non-negative.
- Endpoints are paused (`active = false`) rather than deleted, so old deliveries keep a valid reference.

## Indexes

| Index | Used by |
|---|---|
| `idx_deliveries_status_next_retry (status, next_retry_at)` | The worker's claim query |
| `idx_deliveries_created_at (created_at DESC)` | The newest-first delivery list |
| `idx_events_created_at (created_at DESC)` | The newest-first event list |
| `uq_delivery_attempt_number (delivery_id, attempt_number)` | Loading a delivery's attempts in order |

`signing_secret` is stored in plain text because the worker needs the original secret to sign each attempt; hashing would make signing impossible.
