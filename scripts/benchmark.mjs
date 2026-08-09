const apiBase = process.env.API_BASE ?? "http://localhost:8080/api/v1";
const apiKey = process.env.API_KEY ?? "sd_demo_local_key";
const eventsPerRun = positiveInt(process.env.EVENTS, 100);
const runs = positiveInt(process.env.RUNS, 3);
const concurrency = positiveInt(process.env.CONCURRENCY, 10);
const duplicateRequests = positiveInt(process.env.DUPLICATES, 25);
const pollTimeoutMs = positiveInt(process.env.POLL_TIMEOUT_MS, 120_000);

function positiveInt(raw, fallback) {
  const parsed = Number.parseInt(raw ?? "", 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

async function request(path, init = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-API-Key": apiKey,
      ...init.headers,
    },
  });
  if (!response.ok) {
    throw new Error(`${init.method ?? "GET"} ${path} returned ${response.status}: ${await response.text()}`);
  }
  return response.status === 204 ? undefined : response.json();
}

function eventRequest(eventType, idempotencyKey, sequence) {
  return request("/events", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({
      eventType,
      payload: { benchmark: true, sequence, emittedAt: new Date().toISOString() },
    }),
  });
}

async function mapLimited(values, limit, mapper) {
  const results = new Array(values.length);
  let cursor = 0;
  async function worker() {
    while (cursor < values.length) {
      const index = cursor++;
      results[index] = await mapper(values[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, values.length) }, worker));
  return results;
}

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function fetchDeliveries(createdAfter) {
  const all = [];
  for (let page = 0; ; page += 1) {
    const query = new URLSearchParams({ createdAfter, page: String(page), size: "200" });
    const response = await request(`/deliveries?${query}`);
    all.push(...response.content);
    if (response.last) return all;
  }
}

async function waitForTerminal(eventIds, createdAfter) {
  const wanted = new Set(eventIds);
  const deadline = Date.now() + pollTimeoutMs;
  while (Date.now() < deadline) {
    const matching = (await fetchDeliveries(createdAfter)).filter((delivery) => wanted.has(delivery.eventId));
    if (matching.length === wanted.size && matching.every((delivery) => ["DELIVERED", "DEAD"].includes(delivery.status))) {
      return matching;
    }
    await sleep(100);
  }
  throw new Error(`Timed out after ${pollTimeoutMs}ms waiting for ${wanted.size} terminal deliveries`);
}

function percentile(values, percentileValue) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.max(0, Math.ceil(percentileValue * sorted.length) - 1)];
}

function median(values) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
}

async function duplicateCheck() {
  const key = `benchmark-duplicate-${crypto.randomUUID()}`;
  const responses = await Promise.all(
    Array.from({ length: duplicateRequests }, (_, index) => eventRequest("benchmark.delivery", key, index)),
  );
  const ids = new Set(responses.map((response) => response.eventId));
  return {
    requests: duplicateRequests,
    distinctEventIds: ids.size,
    createdResponses: responses.filter((response) => !response.duplicate).length,
    duplicateResponses: responses.filter((response) => response.duplicate).length,
    passed: ids.size === 1 && responses.filter((response) => !response.duplicate).length === 1,
  };
}

async function successfulDeliveryRun(runNumber) {
  const startedAt = new Date(Date.now() - 1_000).toISOString();
  const token = crypto.randomUUID();
  const sequence = Array.from({ length: eventsPerRun }, (_, index) => index);
  const wallStart = performance.now();
  const ingestion = await mapLimited(sequence, concurrency, (index) =>
    eventRequest("benchmark.delivery", `benchmark-${token}-${index}`, index),
  );
  const deliveries = await waitForTerminal(ingestion.map((response) => response.eventId), startedAt);
  const wallTimeMs = performance.now() - wallStart;
  const details = await mapLimited(deliveries, concurrency, (delivery) => request(`/deliveries/${delivery.id}`));
  const attemptLatencies = details.flatMap((detail) => detail.attempts.map((attempt) => attempt.latencyMs));
  const successful = deliveries.filter((delivery) => delivery.status === "DELIVERED").length;
  const failed = deliveries.filter((delivery) => delivery.status === "DEAD").length;
  return {
    run: runNumber,
    eventsSubmitted: eventsPerRun,
    successfulDeliveries: successful,
    failedDeliveries: failed,
    wallTimeMs: Number(wallTimeMs.toFixed(2)),
    deliveriesPerSecond: Number(((successful + failed) / (wallTimeMs / 1_000)).toFixed(2)),
    attemptLatencyP50Ms: percentile(attemptLatencies, 0.5),
    attemptLatencyP95Ms: percentile(attemptLatencies, 0.95),
  };
}

async function retryCheck() {
  const startedAt = new Date(Date.now() - 1_000).toISOString();
  const response = await eventRequest("benchmark.retry", `benchmark-retry-${crypto.randomUUID()}`, 1);
  const [delivery] = await waitForTerminal([response.eventId], startedAt);
  const detail = await request(`/deliveries/${delivery.id}`);
  return {
    finalStatus: delivery.status,
    attempts: detail.attempts.length,
    httpStatuses: detail.attempts.map((attempt) => attempt.httpStatus),
    passed: delivery.status === "DEAD" && detail.attempts.length === 3,
  };
}

async function main() {
  await request("/dashboard/summary");
  const duplicateIdempotency = await duplicateCheck();
  const successfulRuns = [];
  for (let run = 1; run <= runs; run += 1) {
    successfulRuns.push(await successfulDeliveryRun(run));
  }
  const retryBehavior = await retryCheck();
  const throughput = successfulRuns.map((run) => run.deliveriesPerSecond);
  const output = {
    measuredAt: new Date().toISOString(),
    configuration: { apiBase, eventsPerRun, runs, concurrency, duplicateRequests },
    duplicateIdempotency,
    successfulRuns,
    aggregate: {
      throughputAveragePerSecond: Number((throughput.reduce((sum, value) => sum + value, 0) / throughput.length).toFixed(2)),
      throughputMedianPerSecond: Number(median(throughput).toFixed(2)),
      totalSubmitted: successfulRuns.reduce((sum, run) => sum + run.eventsSubmitted, 0),
      totalSuccessful: successfulRuns.reduce((sum, run) => sum + run.successfulDeliveries, 0),
      totalFailed: successfulRuns.reduce((sum, run) => sum + run.failedDeliveries, 0),
    },
    retryBehavior,
  };
  console.log(JSON.stringify(output, null, 2));
  if (!duplicateIdempotency.passed || !retryBehavior.passed || output.aggregate.totalFailed !== 0) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
