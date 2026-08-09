# Final SDE-1 Audit

## Scorecard

| Area | Score | Evidence and remaining gap |
|---|---:|---|
| Java fundamentals | 8/10 | Records/DTOs, encapsulated entity transitions, exceptions, collections, time/UUID APIs, HMAC/HTTP client, and overflow-aware retry math. No need to manufacture complex generics or concurrency primitives. |
| Spring Boot knowledge | 9/10 | MVC, validation, JPA, transactions, Security filter chain, configuration properties, scheduling, Flyway, Actuator, OpenAPI, exception advice, and profile-based demo data. |
| REST API design | 8/10 | Resource-oriented paths, validation, correct create/duplicate/no-content/error statuses, pagination/filtering, DTOs, request IDs, and OpenAPI. Cursor pagination and a formal problem-details media type are possible improvements. |
| Database design | 9/10 | Normalized delivery graph, JSONB only for event payload, foreign/check/unique constraints, partial/query-driven indexes, optimistic versions, Flyway, and schema validation. |
| Transaction knowledge | 9/10 | Atomic event/job creation, database-authoritative idempotency, short queue claims, pessimistic outcome lock, network I/O outside transactions, leases, and explicit at-least-once limitation. |
| Testing | 8/10 | 14 focused unit tests plus 4 real-PostgreSQL integration tests; external HTTP behavior includes signing and timeout tests. More REST validation/status cases and a multi-worker claim integration test would deepen it. |
| Reliability | 9/10 | Bounded timeouts, retry cap/backoff, persisted attempt history, response truncation, `DEAD` state, manual replay, abandoned-lease recovery, and HMAC headers. Jitter/rate controls and encrypted secrets remain future work. |
| Frontend integration | 8/10 | Purpose-built responsive TypeScript console exercises the full lifecycle, parallel snapshot reads, live polling, mutation feedback, route management, attempt details, and replay. It intentionally lacks a large state framework and end-to-end browser suite. |
| Deployment/run experience | 9/10 | One Compose command, health-gated dependencies, multi-stage non-root backend image, Nginx frontend proxy, environment configuration, seed receivers, OpenAPI, `.env.example`, and CI image build. |
| Interview defensibility | 9/10 | Scope is credible for one fresher, hard choices are documented, failure windows are not hidden, and tests/results are reproducible. The score depends on the candidate actually tracing the code before claiming it. |

Overall assessment: **a strong SDE-1 backend portfolio project with deliberate reliability depth, not a tutorial CRUD app and not an artificially distributed platform.**

## 1. Is anything still unnecessarily SDE-2/SDE-3?

No separate infrastructure is unjustified. `FOR UPDATE SKIP LOCKED`, leases, HMAC, and partial indexes may sound advanced, but each solves a direct delivery-domain failure or concurrency case in a small amount of code. They do not require a second deployable or platform team.

The only concept to present carefully is the PostgreSQL queue. Call it a pragmatic database-backed work queue, not a universally scalable distributed scheduler. Do not add Kafka, Redis, Kubernetes, service discovery, tracing stacks, CQRS, or event sourcing until a measured requirement makes one necessary.

## 2. Is anything too basic or tutorial-like?

No. Basic CRUD would stop at endpoint/event tables. SignalDock adds transactional idempotency, subscription matching, a persisted state machine, safe concurrent claims, lease recovery, external HTTP failure handling, cryptographic signatures, retry scheduling, immutable attempt history, database constraints, manual recovery, real PostgreSQL tests, and measured load utilities.

The deliberately simple areas are appropriate: API-key auth replaces an irrelevant end-user identity product, polling replaces an unnecessary socket lifecycle, and a small wildcard grammar replaces a rule engine.

## 3. Is every technology justified?

| Technology | Why it exists |
|---|---|
| Java 21 / Spring Boot | Primary target stack and cohesive REST, validation, security, persistence, scheduling, and configuration model. |
| Spring Data JPA/Hibernate | Aggregate persistence, relationship loading, pagination, locking, and transaction integration. |
| Native PostgreSQL SQL | Only where database-specific `ON CONFLICT` and `SKIP LOCKED` semantics are the point. |
| PostgreSQL | One durable consistency boundary, JSONB event payload, constraints, partial indexes, queue state, and audit history. |
| Flyway | Versioned, reviewable schema evolution; Hibernate validates rather than creates it. |
| React/TypeScript/Tailwind | Typed, responsive reviewer-facing operations workflow without a heavy component/state platform. |
| Java `HttpClient` | Standard-library outbound HTTP with explicit redirect and timeout behavior. |
| Spring Security | Stateless API-key enforcement and a clear public/private endpoint policy. |
| Actuator / springdoc | Minimal health and browsable API contract. |
| JUnit 5 / AssertJ / Spring Test | Business and API verification. Mockito is available through the test starter but real objects are preferred where simpler. |
| Testcontainers | Verifies PostgreSQL-specific schema, JSONB, native SQL, and constraints against the actual database engine. |
| Docker / Compose / Nginx | Reproducible three-container local runtime and frontend-to-backend proxy. |
| GitHub Actions | Repeatable backend, frontend, and image-build checks. |
| Node benchmark script | Uses built-in fetch/performance APIs to avoid another load-test platform while keeping the workload reproducible. |

## 4. Can every resume bullet be proven?

Yes, within the precise wording in `docs/RESUME_BULLETS.md`.

- Stack, modules, queue transitions, signatures, timeouts, retries, API/security/database features, tests, Docker, and documentation are directly inspectable in source and migrations.
- The count of 18 tests comes from the passing Maven test run: 14 unit tests and 4 integration tests.
- Version B numbers appear verbatim in `docs/BENCHMARK_RESULTS.md` and are reproducible with `scripts/benchmark.mjs`.
- There is no percentage improvement, production-scale claim, user count, “exactly once,” “enterprise-grade,” or “100% secure” claim.

Do not convert the measured 300-event local result into a higher scale, remove its environment context in conversation, or claim that zero failures means zero possible duplicates.

## 5. Ten most likely interview questions

1. **Why did you replace Kafka with PostgreSQL?** Because atomic event/job creation matters more than independent scale here; it removes the database/broker dual-write gap. A broker would require a transactional outbox.
2. **How does idempotency remain correct under concurrent requests?** A unique database constraint and `INSERT ... ON CONFLICT DO NOTHING`, not an application check, decide the winner.
3. **How do multiple workers avoid claiming the same delivery?** A short transaction selects due rows `FOR UPDATE SKIP LOCKED` and marks each with `PROCESSING`, worker ID, and lease expiry.
4. **What happens if the worker crashes at each step?** Before claim commit: no change. After claim: lease expires. After remote success/before outcome commit: request may repeat, which is why delivery is at least once.
5. **Why is the HTTP request outside the transaction?** To avoid holding a connection/row lock for a receiver's timeout and to keep transaction duration predictable.
6. **How is the signature computed and verified?** HMAC-SHA256 over `timestamp + "." + exact raw payload`; the receiver checks freshness and compares a recomputed signature in constant time.
7. **How does retry timing work?** `min(baseDelay × 2^(attempt-1), maxDelay)`; max attempts moves the row to `DEAD`, and manual replay adds budget without erasing history.
8. **Which invariants are enforced by PostgreSQL?** Unique idempotency key, one delivery per event/endpoint, unique attempt number, unique endpoint route, valid states/counts, and foreign-key relationships.
9. **Why use Testcontainers instead of H2?** The implementation relies on PostgreSQL JSONB, partial indexes, constraints, `ON CONFLICT`, and locking SQL that H2 cannot faithfully verify.
10. **What do the benchmark numbers prove?** Only the recorded local topology/workload: three 1,000-event runs, 3,000 successes, the observed throughput/latency, 100-request idempotency check, and three-attempt 503 path—not production capacity.

## 6. What to understand before adding it to a resume

1. Trace a new event from `EventController` through `EventIngestionService` to `events` and `deliveries` in one transaction.
2. Explain why check-then-insert is racy and why a database unique constraint is the final authority.
3. Run the queue SQL manually and explain `FOR UPDATE`, `SKIP LOCKED`, the partial queue index, and lease recovery.
4. Draw the delivery state machine without looking at the document, including allowed manual replay and terminal state.
5. Explain every transaction boundary and the duplicate window around an external HTTP call.
6. Recompute a sample HMAC and describe timestamp freshness and exact raw-body requirements.
7. Calculate the first four retry delays and show where the configured cap applies.
8. Read the Flyway migration and justify each foreign key, uniqueness rule, check, and major index.
9. Run `mvn test`, identify which tests use real PostgreSQL and which test the external HTTP boundary, and explain what remains untested.
10. Run the Compose demo and benchmark yourself; be able to explain the environment, first-run variance, and why the numbers are not production claims.
11. Know the security limitations: global API keys, local admin default, plaintext signing secret, browser local storage, SSRF/DNS rebinding, and no per-destination rate policy.

If any of those points cannot yet be explained, use the project as a learning exercise first and add the corresponding resume bullet only afterward.
