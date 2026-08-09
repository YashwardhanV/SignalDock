# Resume Bullets

Use only the version you can explain and reproduce. Keep the project link near these bullets so an interviewer can inspect the implementation and documentation.

## Version A — no benchmark metrics

- Built SignalDock, a React/TypeScript and Spring Boot modular monolith that registers event receivers, ingests idempotent JSON events, and exposes paginated delivery/attempt history backed by PostgreSQL and Flyway.
- Implemented a transactional PostgreSQL delivery queue with row-level `SKIP LOCKED` claims, expiring leases, HMAC-SHA256 signatures, bounded HTTP timeouts, exponential backoff, terminal failure state, and manual replay.
- Added hash-only API-key authentication, DTO validation, centralized API errors, relational constraints/indexes, Docker Compose, OpenAPI documentation, and 18 focused JUnit/Testcontainers tests covering business and database behavior.

## Version B — benchmark-backed

- Built SignalDock, a React/TypeScript and Spring Boot modular monolith for registering receivers, accepting idempotent events, and tracking signed HTTP delivery attempts in PostgreSQL through Flyway-managed schemas.
- Implemented database-backed idempotency and queue processing that collapsed 100 concurrent same-key requests to one event and verified a three-attempt HTTP 503 path ending in persisted `DEAD` state.
- Completed 3,000 of 3,000 success-path deliveries with zero terminal failures across three local Docker runs, observing 54.74 deliveries/second median throughput and per-run outbound-attempt p95 latency between 5 and 7 ms.
