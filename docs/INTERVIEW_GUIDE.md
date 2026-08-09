# SignalDock Interview Guide

Do not memorize only the concise answers. Use them after you can trace the code and explain the failure paths in your own words.

## 1. Modular monolith and technology scope

**Problem it solves:** The domain needs asynchronous delivery and retry behavior, but a portfolio project should remain locally runnable and internally consistent.

**How it works:** React calls one stateless Spring Boot REST API. Backend packages own distinct domain responsibilities but call one another in process. PostgreSQL stores configuration, accepted events, queue state, and history. A scheduled component performs background work.

**Important classes:** `SignalDockApplication`, `SecurityConfig`, `EventIngestionService`, `DeliveryWorker`, `AppProperties`.

**Tables:** All six tables share one schema and consistency boundary.

**Alternatives considered:** A broker plus transactional outbox would also be possible.

**Failure cases:** One application/database is a shared failure domain. If the backend is stopped, ingestion and processing both stop; persisted due work remains and resumes after restart.

**Tradeoff:** Deployment and consistency are simpler, while independent scaling and team ownership are limited. This is appropriate until measurements show those limitations matter.

**Likely question:** Why is this not a microservice system?

**Concise answer:** “There is one owner and one deployment requirement, and event acceptance must atomically create delivery work. A modular monolith gives clear boundaries and one transaction. I would split only for measured independent-scaling or organizational needs, then introduce an outbox before a broker.”

## 2. Idempotent event ingestion and route matching

**Problem it solves:** Clients retry after timeouts and can send the same request concurrently. Creating a new event on every retry would duplicate every downstream delivery. One endpoint can also match more than one route.

**How it works:** `EventIngestionService` requires and normalizes `Idempotency-Key`. `EventRepository.insertIfAbsent` uses PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`. A conflict loads and returns the original event. For a new row, active subscriptions are matched using `EventPatternMatcher`; a `LinkedHashMap` deduplicates matched endpoint IDs before one `Delivery` per endpoint is saved. The entire operation is one transaction.

**Important classes:** `EventController`, `EventIngestionService`, `EventRepository`, `EventPatternMatcher`, `SubscriptionRepository`.

**Tables:** `events`, `endpoint_subscriptions`, `webhook_endpoints`, `deliveries`.

**Alternatives considered:** Check-then-insert in Java, in-memory idempotency cache, Redis, regular-expression routing, or Kafka consumer deduplication.

**Failure cases:** A Java check before insert races under concurrency. Returning a different payload for an existing key is currently treated as the same operation; a future version could persist a request hash and reject mismatched reuse. Loading every active subscription will become inefficient if the routing table becomes very large.

**Tradeoff:** A global unique idempotency key is simple but not tenant-scoped. The small matcher supports exact, `*`, and trailing wildcard patterns; it avoids an opaque rule engine.

**Likely question:** Why use the database instead of `synchronized`?

**Concise answer:** “`synchronized` protects only one JVM and still does not establish the database invariant. The unique constraint is authoritative across threads and instances, and `ON CONFLICT` turns the expected race into a normal result.”

**Likely question:** What does a duplicate request return?

**Concise answer:** “The first accepted request returns 201. A repeated idempotency key returns 200 with the same event ID, zero newly created deliveries, and `duplicate=true`.”

## 3. PostgreSQL-backed queue, concurrent claims, and leases

**Problem it solves:** HTTP delivery must happen after event acceptance, failures must be retried later, two workers must not normally process the same due row, and a crashed worker must not strand it forever.

**How it works:** Deliveries contain status, due time, lease time, and worker identity. `DeliveryQueueService.claimDue` runs a native query for due `PENDING`/`RETRY_PENDING` rows plus expired `PROCESSING` leases. `FOR UPDATE SKIP LOCKED` lets concurrent claimers take different rows. Claimed rows become `PROCESSING` with `lease_until`, then the short transaction commits.

**Important classes:** `DeliveryWorker`, `DeliveryQueueService`, `DeliveryRepository`, `Delivery`.

**Tables:** `deliveries`; source and audit relationships lead to `events`, `webhook_endpoints`, and `delivery_attempts`.

**Alternatives considered:** Kafka/RabbitMQ, a table without locking, advisory locks, or holding a row lock through the HTTP call.

**Failure cases:** A crash before the claim commit changes nothing. A crash after claim commit leaves a processing row until its lease expires. A crash after the remote receiver accepts but before the outcome commit can cause a repeated outbound request.

**Tradeoff:** This is at-least-once delivery, not exactly once. PostgreSQL is easy to reason about and makes enqueue atomic with ingestion; a broker would handle much larger bursts and separate consumers better but adds a dual-write/outbox requirement.

**Likely question:** What exactly does `SKIP LOCKED` buy you?

**Concise answer:** “Without it, a second worker waits on rows the first worker is claiming. With it, locked rows are skipped so each worker can claim another due batch. The state/lease update still happens inside the same transaction as the selection.”

**Likely question:** Can it ever send a duplicate?

**Concise answer:** “Yes, in the crash window after receiver success and before local outcome commit. Exactly-once HTTP cannot be guaranteed by this sender. I expose a stable delivery ID so the receiver can make its side idempotent.”

## 4. Transaction boundaries and state machine

**Problem it solves:** Long external calls must not consume a database transaction, while queue transitions and attempt history must remain consistent.

**How it works:** Claim, prepare, network, and outcome are separate steps. The HTTP request runs after the claim transaction commits. `recordOutcome` locks the delivery again, calculates the next attempt number, inserts one `DeliveryAttempt`, and changes delivery state in one transaction. Entity methods define allowed changes such as `claim`, `markDelivered`, `markFailedAttempt`, and `manualRetry`.

**Important classes:** `DeliveryProcessingService`, `DeliveryHttpClient`, `Delivery`, `DeliveryAttempt`, `DeliveryRepository`.

**Tables:** `deliveries`, `delivery_attempts`.

**Alternatives considered:** One large `@Transactional` worker method, database triggers, or a generic workflow engine.

**Failure cases:** The outcome transaction can fail after an HTTP response; the lease recovery path repeats the request. A stale outcome is ignored if the row is no longer `PROCESSING`. Unique attempt numbering prevents two rows with the same attempt sequence.

**Tradeoff:** More service calls and explicit states make the code longer, but the resource lifetime and recovery behavior are visible. A workflow engine would hide too much for this project's scope.

**Likely question:** Why is the HTTP call outside the transaction?

**Concise answer:** “The receiver can take up to the read timeout. Holding a DB connection and row locks during that wait reduces throughput and creates lock contention. Short transactions claim and finalize; a lease covers the gap.”

## 5. Retry policy, terminal failure, and manual replay

**Problem it solves:** Temporary receiver/network failures should recover automatically, but repeated failures must stop consuming resources and remain diagnosable.

**How it works:** Failure increments `attempt_count`. If attempts remain, the next due time is `now + min(base × 2^(attempt-1), cap)` and state becomes `RETRY_PENDING`; otherwise state becomes `DEAD`. Every call records timestamps, HTTP status or error, bounded response/error text, and latency. Manual replay is allowed only from `DEAD`; it preserves attempts and adds the endpoint's configured attempt allowance.

**Important classes:** `RetryPolicy`, `DeliveryProcessingService`, `DeliveryQueryService`, `Delivery`, `DeliveryAttempt`.

**Tables:** `deliveries`, `delivery_attempts`, `webhook_endpoints`.

**Alternatives considered:** Fixed delay, immediate retry, infinite retry, Kafka DLQ, or deleting/resetting old attempts on replay.

**Failure cases:** A long delay can postpone recovery; a short delay can overload a failing receiver. This implementation has no random jitter, so many simultaneous failures may become due together. Manual retry can repeat a permanent bad request.

**Tradeoff:** Exponential backoff is understandable and bounded. Jitter and per-destination rate policy are valuable future work after measuring correlated failures. Persisted `DEAD` state is simpler to inspect atomically than a separate broker DLQ.

**Likely question:** Why not reset `attempt_count` to zero on manual retry?

**Concise answer:** “That would falsify operational history and can conflict with unique attempt numbers. I keep every attempt and increase the maximum budget, so the audit trail stays monotonic.”

**Likely question:** Why is there no wait-time estimate API?

**Concise answer:** “The system exposes `nextRetryAt`, which is exact for policy scheduling but not a promise of start time. A queue wait estimate would need measured service time, concurrency, and receiver-specific constraints; inventing one would be misleading.”

## 6. HTTP delivery, signatures, timeouts, and URL safety

**Problem it solves:** Receivers need evidence that a payload came from the configured sender, while the sender must bound slow/untrusted network behavior.

**How it works:** `HmacSignatureService` signs `unixSeconds + "." + rawPayload` using the endpoint secret and HMAC-SHA256. `DeliveryHttpClient` uses Java's `HttpClient`, disables redirects, applies connection/read timeouts, sends identity/signature headers, treats 2xx as success, and captures a truncated response or exception. `EndpointUrlValidator` requires HTTPS/public addresses by default and checks all DNS answers.

**Important classes:** `HmacSignatureService`, `DeliveryHttpClient`, `EndpointUrlValidator`, `AppProperties`.

**Tables:** `webhook_endpoints` stores the signing secret; `delivery_attempts` stores outcomes.

**Alternatives considered:** Plain shared-secret header, JWT, asymmetric signatures, unlimited client timeout, automatic redirect following, or accepting any URL.

**Failure cases:** Receivers must use exact raw body bytes and reject stale timestamps. DNS can change between validation and connection. Timeouts are ambiguous: the receiver may have processed a request whose response was not received. Secrets stored as plaintext are exposed if the database/application is compromised.

**Tradeoff:** HMAC is interoperable and compact but needs shared-secret distribution/rotation. Timestamped input enables replay-window enforcement on the receiver, which SignalDock cannot enforce for it. Production should encrypt stored secrets and consider resolving/connecting through a controlled egress layer.

**Likely question:** Why include the timestamp in the signed input?

**Concise answer:** “Signing only the body proves integrity but lets a captured request be replayed indefinitely. A receiver can reject an old timestamp before comparing the HMAC.”

**Likely question:** Does URL validation make SSRF impossible?

**Concise answer:** “No. It blocks obvious non-HTTPS and private/link-local/loopback destinations at registration, including all initial DNS answers. DNS rebinding remains, so I document the limitation instead of claiming complete SSRF prevention.”

## 7. Relational design, pagination, and REST contract

**Problem it solves:** Operational history must stay queryable and consistent as records grow; APIs should not leak persistence internals or return unbounded lists.

**How it works:** Flyway creates normalized tables, foreign keys, check constraints, unique invariants, and query-driven indexes. JPA entities model writes/relationships; DTO records shape API data. `PageResponse` standardizes paginated list metadata. `GlobalExceptionHandler` produces validation/conflict/not-found responses. New event/endpoint resources return 201; duplicates return 200; delete-like route deactivation returns 204; auth failures return 401.

**Important classes:** `PageResponse`, controller/DTO classes, `GlobalExceptionHandler`, JPA repositories; migration `V1__initial_schema.sql`.

**Tables:** `api_keys`, `webhook_endpoints`, `endpoint_subscriptions`, `events`, `deliveries`, `delivery_attempts`.

**Alternatives considered:** Hibernate `ddl-auto=create`, JSON documents for attempts, exposing entities, offset-free cursor pagination, or putting every index on every column.

**Failure cases:** Offset pagination can shift while new rows arrive and becomes slower at very deep pages. Too many indexes slow writes. Cascade deletion is used only where history ownership is clear; endpoints are deactivated rather than deleted to preserve references.

**Tradeoff:** Offset pagination is familiar and sufficient for this UI. A `(created_at, id)` cursor is a defensible next step after deep-page measurements. Flyway plus `ddl-auto=validate` catches mapping/schema drift without letting Hibernate mutate deployed schema.

**Likely question:** Why both JPA and a native SQL insert/query?

**Concise answer:** “JPA is useful for aggregates and ordinary queries. PostgreSQL-specific `ON CONFLICT` and `FOR UPDATE SKIP LOCKED` express critical concurrency behavior directly. I isolate those queries in repositories instead of forcing an awkward portable abstraction.”

## 8. API-key security and secret lifecycle

**Problem it solves:** Machine clients need simple API access without a full user login system, and leaked database contents should not immediately reveal usable API keys.

**How it works:** The admin endpoint generates a random raw key, stores its SHA-256 hash, and returns the raw value once. `ApiKeyAuthenticationFilter` hashes `X-API-Key`, looks up an active row, and creates a stateless authenticated request. Later API responses never contain key hashes. Endpoint signing secrets are likewise shown once on creation and later represented as hints.

**Important classes:** `ApiKeyAdminController`, `ApiKeyAuthenticationFilter`, `ApiKeyHasher`, `SecurityConfig`, `EndpointService`.

**Tables:** `api_keys`, `webhook_endpoints`.

**Alternatives considered:** Spring sessions, OAuth2/JWT, storing raw API keys, or no authentication for a demo.

**Failure cases:** A weak raw key is vulnerable to offline guessing, so generation must provide high entropy. The admin key is environment-provided and its local default must never be deployed. There is no tenant scope or actor-level audit yet.

**Tradeoff:** SHA-256 is appropriate for high-entropy generated tokens and fast request verification; a slow password hash is intended for low-entropy human passwords. OAuth would be unnecessary scope for the current machine API.

**Likely question:** Why can signing secrets not be hashed like API keys?

**Concise answer:** “API-key verification only needs a one-way comparison. HMAC delivery must recover the original signing secret, so it needs encryption or a secret-manager reference, not a hash.”

## 9. Testing and benchmarking

**Problem it solves:** Reliability and performance claims must be reproducible rather than asserted from code appearance.

**How it works:** Unit tests isolate retry calculation, matching, HMAC bytes, state transitions, URL rules, and the HTTP client's signing/timeout behavior. `ApiIntegrationTest` starts real PostgreSQL 16 through Testcontainers, lets Flyway create the schema, and exercises authentication, concurrent-style idempotent API behavior, DB constraints, and manual retry. `scripts/benchmark.mjs` uses real REST calls, polls terminal delivery state, fetches attempts, and fails on correctness regressions.

**Important classes/files:** All `backend/src/test/java` tests, `ApiIntegrationTest`, `scripts/benchmark.mjs`, `.github/workflows/ci.yml`.

**Tables:** Integration tests touch the full delivery graph; benchmark measurements come from `events`, `deliveries`, and `delivery_attempts` through public APIs.

**Alternatives considered:** H2 integration tests, mocked repositories everywhere, a coverage-percentage target, manual stopwatch results, or made-up resume improvements.

**Failure cases:** Testcontainers needs Docker. A local benchmark includes warm-up, OS scheduling, shared-resource, and loopback-receiver effects. Three runs do not establish production capacity.

**Tradeoff:** Real PostgreSQL tests are slower but verify JSONB, partial indexes, constraints, and native concurrency SQL that H2 cannot faithfully represent. The benchmark favors repeatability and correctness over sophisticated load-tool infrastructure.

**Likely question:** What can you honestly claim from the benchmark?

**Concise answer:** “On the documented laptop and local Docker topology, the script completed 3,000/3,000 success-path deliveries across three 1,000-event runs, measured the recorded latency and throughput, collapsed 100 concurrent duplicate requests, and verified three 503 attempts ending in DEAD. I cannot generalize that to production scale.”

## 10. React operations console

**Problem it solves:** A reviewer needs to see configuration, ingestion, state changes, attempts, and replay without building curl commands.

**How it works:** `App` owns the selected view, saved demo key, snapshot, and feedback. `getSnapshot` fetches independent API resources in parallel. Views/components render summary cards, a delivery pulse, event composer/history, endpoint/route management, delivery filtering, and an attempt detail timeline. A four-second poll refreshes the snapshot; mutation actions refresh immediately.

**Important files:** `App.tsx`, `api.ts`, `OverviewView.tsx`, `EventsView.tsx`, `EndpointsView.tsx`, `DeliveriesView.tsx`, reusable components, `index.css`.

**Tables:** The UI reads all domain tables through DTO-based REST APIs; it never couples to the database.

**Alternatives considered:** Server-rendered templates, WebSockets/SSE, Redux, or a generic admin-table theme.

**Failure cases:** The latest-100 snapshot omits older records; polling can overlap with a slow request without cancellation; the key is stored in browser local storage for demo convenience. It should not be treated as a hardened secret store.

**Tradeoff:** Direct React state and a small typed API layer keep ownership clear at this size. WebSockets, a global state library, and a full design system would add more lifecycle and abstraction than the UI currently needs.

**Likely question:** Why poll instead of WebSockets?

**Concise answer:** “This is a low-frequency operations dashboard, not a live collaboration tool. Four-second polling is stateless, observable, and easy to recover. I would move to SSE/WebSockets if measured polling traffic or freshness requirements justified it.”

## A two-minute project explanation

“SignalDock accepts idempotent events and reliably posts them to registered receivers. I kept it as a Spring Boot modular monolith with PostgreSQL because event acceptance and delivery-job creation need one transaction. A scheduled worker claims due rows using `FOR UPDATE SKIP LOCKED`, commits a lease, performs a timestamped HMAC-signed HTTP request outside the database transaction, and then records an immutable attempt plus the next state. Failures use bounded exponential backoff and eventually become `DEAD`; manual replay preserves the attempt audit trail. The React console makes that lifecycle visible. I test database-specific behavior with Testcontainers and publish only measurements produced by the checked-in benchmark. The main honest limitation is at-least-once delivery: a crash after remote success but before local commit can duplicate a request, so receivers get a stable delivery ID for deduplication.”
