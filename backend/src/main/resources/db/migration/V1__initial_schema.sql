CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    signing_secret VARCHAR(180) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    max_attempts INTEGER NOT NULL DEFAULT 5 CHECK (max_attempts BETWEEN 1 AND 12),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE endpoint_subscriptions (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    event_pattern VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_endpoint_pattern UNIQUE (endpoint_id, event_pattern)
);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    payload JSONB NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_created_at ON events (created_at DESC);

CREATE TABLE deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id),
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    max_attempts INTEGER NOT NULL CHECK (max_attempts >= 1),
    next_retry_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    last_error TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_delivery_event_endpoint UNIQUE (event_id, endpoint_id),
    CONSTRAINT ck_delivery_status CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_PENDING', 'DELIVERED', 'DEAD'))
);

-- Used by the worker's claim query (status + due time).
CREATE INDEX idx_deliveries_status_next_retry ON deliveries (status, next_retry_at);
-- Used by the dashboard list (newest first).
CREATE INDEX idx_deliveries_created_at ON deliveries (created_at DESC);

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL CHECK (attempt_number >= 1),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    http_status INTEGER,
    response_body VARCHAR(4000),
    error_message VARCHAR(4000),
    latency_ms BIGINT NOT NULL CHECK (latency_ms >= 0),
    -- Also serves as the index for "attempts of a delivery, in order".
    CONSTRAINT uq_delivery_attempt_number UNIQUE (delivery_id, attempt_number)
);
