-- A new token is written every time a worker claims a delivery. Only the worker holding the
-- current token may record an outcome, so a worker whose lease expired cannot overwrite the
-- result of the worker that reclaimed the delivery.
ALTER TABLE deliveries ADD COLUMN claim_token UUID;

-- Endpoint health: how many deliveries in a row ended DEAD, and why the endpoint was paused
-- automatically (NULL when it is active or was paused by a person).
ALTER TABLE webhook_endpoints
    ADD COLUMN consecutive_dead_deliveries INTEGER NOT NULL DEFAULT 0
        CHECK (consecutive_dead_deliveries >= 0);
ALTER TABLE webhook_endpoints ADD COLUMN disabled_reason VARCHAR(500);
ALTER TABLE webhook_endpoints ADD COLUMN disabled_at TIMESTAMPTZ;

-- The claim query ranks due deliveries per endpoint; this keeps that ranking an index scan.
CREATE INDEX idx_deliveries_endpoint_next_retry ON deliveries (endpoint_id, next_retry_at);
