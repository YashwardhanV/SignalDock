# Benchmark Results

These are observed local results, not capacity claims. The workload, receiver, database, and application all ran on one laptop, so numbers mainly make future changes comparable on the same environment. Nothing is extrapolated to a production deployment or a larger dataset.

## Environment

| Item | Measured value |
|---|---|
| Date/time | 2026-08-07 17:38:36 IST (2026-08-07T12:08:36.998Z) |
| Host OS | Windows 11 Home Single Language, 10.0.26200 |
| CPU | Intel Core i5-10200H @ 2.40 GHz, 4 cores / 8 logical processors |
| Host RAM | 7.78 GiB visible |
| Docker | Docker Desktop Engine 29.6.2, Linux x86_64, 8 CPUs, 3,986,915,328 bytes allocated memory |
| Application images | Eclipse Temurin Java 21 JRE backend; PostgreSQL 16 Alpine; Nginx 1.27 Alpine frontend |
| Benchmark client | Node.js v24.19.0 on the host |
| Topology | Backend, PostgreSQL, demo receivers, and frontend in local Docker Compose; benchmark client on the same host |
| Host ports used | API 18080 and UI 13000 because unrelated local containers already occupied documented defaults 8080/3000 |

## Workload and command

The script ran three independent success-path batches of 1,000 `benchmark.delivery` events at client concurrency 25. Every event matched one seeded receiver that returned HTTP 202. End-to-end wall time starts immediately before ingestion and ends when all 1,000 associated deliveries are terminal. Throughput is terminal deliveries divided by that wall time. Attempt latency is the backend-measured duration of the outbound receiver HTTP call.

It separately sent 100 concurrent requests with the same idempotency key and created one `benchmark.retry` event against a deterministic HTTP 503 receiver.

```powershell
$env:API_BASE='http://localhost:18080/api/v1'
$env:EVENTS='1000'
$env:RUNS='3'
$env:CONCURRENCY='25'
$env:DUPLICATES='100'
$env:POLL_TIMEOUT_MS='240000'
node scripts/benchmark.mjs
```

The application used the checked-in Compose demo settings: queue batch size 20, 100 ms polling, 250 ms retry base delay, one-second retry-delay cap, two-second connect timeout, and four-second read timeout.

## Successful delivery results

| Run | Events | Delivered | `DEAD` failures | Wall time | Deliveries/second | Attempt p50 | Attempt p95 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1,000 | 1,000 | 0 | 22,325.25 ms | 44.79 | 3 ms | 7 ms |
| 2 | 1,000 | 1,000 | 0 | 18,266.85 ms | 54.74 | 2 ms | 6 ms |
| 3 | 1,000 | 1,000 | 0 | 14,191.59 ms | 70.46 | 2 ms | 5 ms |

- Total success workload: **3,000 submitted, 3,000 delivered, 0 terminal failures**.
- Average observed throughput across the three runs: **56.66 deliveries/second**.
- Median observed throughput across the three runs: **54.74 deliveries/second**.
- The first run was slower than the later two; it was retained rather than discarded as a warm-up outlier.

## Duplicate idempotency result

| Concurrent requests | Distinct event IDs | Created responses | Duplicate responses | Result |
|---:|---:|---:|---:|---|
| 100 | 1 | 1 | 99 | Pass |

All responses referred to the same event ID. This verifies the database uniqueness/`ON CONFLICT` path under this workload; it does not prove behavior at an untested concurrency level.

## Retry behavior result

The deterministic failure receiver returned HTTP 503 three times. SignalDock recorded three attempt rows and ended in `DEAD`, matching the seeded endpoint's `maxAttempts = 3`. Result: **Pass**.

## Failures and caveats

- Benchmark correctness failures: none.
- Successful-workload terminal failures: zero.
- No 100,000-event run was attempted on this 7.78 GiB laptop; no larger result is implied.
- The success receiver is in the same backend/container network, so these HTTP latencies do not represent an internet receiver.
- Three runs are sufficient for a reproducible portfolio baseline, not a statistically rigorous capacity study.
- The benchmark shares CPU, memory, and storage with PostgreSQL and the receiver. Remote databases or receivers can change results substantially.

## How to reproduce or extend

Run `docker compose up --build`, then `node scripts/benchmark.mjs`. Keep raw output when changing `EVENTS`, `RUNS`, `CONCURRENCY`, worker settings, hardware, or receiver topology; add a new dated section instead of overwriting this baseline. If a run fails or times out, record the failure rather than extrapolating from a smaller run.
