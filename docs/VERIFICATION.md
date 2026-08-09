# Verification Record

Verified locally on 2026-08-07 after the final dependency and source changes.

## Backend

Command: `mvn test`

Result: **pass — 18 tests, 0 failures, 0 errors, 0 skipped**.

| Test class | Tests | Scope |
|---|---:|---|
| `DeliveryHttpClientTest` | 2 | Signed identity headers, HTTP success capture, read timeout failure. |
| `DeliveryStateTest` | 3 | Retry-pending, max-attempt `DEAD`, manual replay budget/history. |
| `HmacSignatureServiceTest` | 1 | Deterministic timestamp/raw-payload HMAC-SHA256 output. |
| `RetryPolicyTest` | 2 | Exponential growth and configured cap. |
| `EndpointUrlValidatorTest` | 3 | Scheme, private/local address, and public HTTPS rules. |
| `EventPatternMatcherTest` | 3 | Exact, wildcard, and nonmatching routes. |
| `ApiIntegrationTest` | 4 | API-key rejection, idempotent REST/database behavior, endpoint uniqueness, manual retry. |

The integration class used Testcontainers 1.21.4 with PostgreSQL 16; Flyway created the schema and Hibernate validated it. The application uses Spring Boot 3.5.16 and Java 21 as its compilation/container target.

## Frontend

- `npm audit --audit-level=high`: **0 vulnerabilities reported** at verification time.
- `npm run build`: **pass** (`tsc --noEmit` plus Vite 8.2.1 production build).
- Output after the ownership/footer update: HTML 0.56 kB (0.33 kB gzip), CSS 18.89 kB (5.13 kB gzip), JavaScript 170.53 kB (54.50 kB gzip).

The UI implementation follows a compact React structure: typed API boundary, parallel independent reads, versioned local-storage key, extracted reusable components/views, direct state ownership, labels/roles, and responsive desktop/tablet/mobile layouts.

## Docker Compose smoke check

- Backend image built from the checked-in multi-stage Dockerfile.
- PostgreSQL health: healthy.
- Backend `/actuator/health`: `UP` with liveness/readiness groups.
- Frontend: HTTP 200 through Nginx.
- Backend ran against the Compose PostgreSQL hostname and Flyway schema version 1.

Unrelated local containers occupied host ports 8080 and 3000 during verification, so SignalDock was mapped to 18080 and 13000 for this run. The checked-in defaults remain 8080 and 3000 for a clean machine.

## Load and failure behavior

The final Compose image was benchmarked after the checks above. See `BENCHMARK_RESULTS.md` for the environment, command, all three run rows, duplicate result, retry result, caveats, and resume-safe measurements.
