package dev.signaldock.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.signaldock.delivery.ClaimedDelivery;
import com.sun.net.httpserver.HttpServer;
import dev.signaldock.delivery.DeliveryResult;
import dev.signaldock.delivery.DeliveryWorker;
import dev.signaldock.delivery.DeliveryWorkerService;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Drives the delivery queue directly against PostgreSQL. These tests commit for real (no
 * @Transactional rollback), because locking only means something across separate transactions.
 */
class DeliveryQueueIntegrationTest extends PostgresIntegrationTest {
    @Autowired DeliveryWorkerService workerService;
    @Autowired DeliveryWorker deliveryWorker;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;

    @BeforeEach
    void emptyTheQueue() {
        jdbc.update("delete from delivery_attempts");
        jdbc.update("delete from deliveries");
    }

    // ---------------------------------------------------------------- concurrency

    @Test
    void concurrentWorkersDeliverEveryDeliveryExactlyOnceAndRespectThePerEndpointCap() throws Exception {
        int endpoints = 10;
        int deliveriesPerEndpoint = 30;
        int workers = 8;
        Map<UUID, UUID> endpointOfDelivery = new ConcurrentHashMap<>();
        for (int e = 0; e < endpoints; e++) {
            UUID endpointId = createEndpoint(true, 5);
            for (int d = 0; d < deliveriesPerEndpoint; d++) {
                endpointOfDelivery.put(createDueDelivery(endpointId, Instant.now().minusSeconds(60)), endpointId);
            }
        }
        int total = endpoints * deliveriesPerEndpoint;

        Map<UUID, AtomicInteger> claimsPerDelivery = new ConcurrentHashMap<>();
        Map<UUID, AtomicInteger> inFlightPerEndpoint = new ConcurrentHashMap<>();
        AtomicInteger highestInFlightSeen = new AtomicInteger();
        AtomicInteger recorded = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<?>> results = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            results.add(pool.submit(() -> {
                start.await();
                while (recorded.get() < total && System.nanoTime() < deadline) {
                    List<ClaimedDelivery> claimed = workerService.claimDue();
                    if (claimed.isEmpty()) {
                        Thread.sleep(2);
                        continue;
                    }
                    // Counted after the claim committed and before the outcome commits, so the
                    // observed number can only be lower than the real one, never higher.
                    for (ClaimedDelivery claim : claimed) {
                        claimsPerDelivery.computeIfAbsent(claim.deliveryId(), id -> new AtomicInteger()).incrementAndGet();
                        int now = inFlightPerEndpoint
                                .computeIfAbsent(endpointOfDelivery.get(claim.deliveryId()), id -> new AtomicInteger())
                                .incrementAndGet();
                        highestInFlightSeen.accumulateAndGet(now, Math::max);
                    }
                    for (ClaimedDelivery claim : claimed) {
                        inFlightPerEndpoint.get(endpointOfDelivery.get(claim.deliveryId())).decrementAndGet();
                        if (workerService.recordOutcome(claim, success())) {
                            recorded.incrementAndGet();
                        }
                    }
                }
                return null;
            }));
        }
        try {
            start.countDown();
            for (Future<?> result : results) {
                result.get(90, TimeUnit.SECONDS);
            }
        } finally {
            // Even if an assertion inside a worker failed, stop every thread before the next test runs.
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(recorded.get()).isEqualTo(total);
        assertThat(claimsPerDelivery).hasSize(total);
        assertThat(claimsPerDelivery.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        assertThat(highestInFlightSeen.get()).isLessThanOrEqualTo(MAX_IN_FLIGHT_PER_ENDPOINT);
        assertThat(count("select count(*) from deliveries where status = 'DELIVERED'")).isEqualTo(total);
        assertThat(count("select count(*) from delivery_attempts")).isEqualTo(total);
    }

    @Test
    void oneEndpointWithABigBacklogCannotStarveTheOthers() {
        UUID busy = createEndpoint(true, 5);
        UUID quietA = createEndpoint(true, 5);
        UUID quietB = createEndpoint(true, 5);
        for (int i = 0; i < 50; i++) {
            createDueDelivery(busy, Instant.now().minusSeconds(600)); // older, so first in line
        }
        UUID a = createDueDelivery(quietA, Instant.now().minusSeconds(1));
        UUID b = createDueDelivery(quietB, Instant.now().minusSeconds(1));

        List<ClaimedDelivery> claimed = workerService.claimDue();

        Map<UUID, Long> perEndpoint = claimed.stream()
                .collect(Collectors.groupingBy(c -> endpointOf(c.deliveryId()), Collectors.counting()));
        assertThat(perEndpoint).containsEntry(busy, (long) MAX_IN_FLIGHT_PER_ENDPOINT);
        assertThat(claimed).extracting(ClaimedDelivery::deliveryId).contains(a, b);

        // The busy endpoint is at its cap until one of its deliveries finishes.
        assertThat(workerService.claimDue()).isEmpty();
        workerService.recordOutcome(claimed.stream().filter(c -> endpointOf(c.deliveryId()).equals(busy)).findFirst().orElseThrow(), success());
        assertThat(workerService.claimDue()).hasSize(1);
    }

    @Test
    void slowReceiversAreSentInParallelInsteadOfOneAfterAnother() throws Exception {
        HttpServer receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.setExecutor(Executors.newCachedThreadPool());
        receiver.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1_000);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        receiver.start();
        try {
            String url = "http://127.0.0.1:" + receiver.getAddress().getPort() + "/slow";
            for (int i = 0; i < 4; i++) {
                UUID endpoint = createEndpoint(true, 5);
                jdbc.update("update webhook_endpoints set url = ? where id = ?", url, endpoint);
                createDueDelivery(endpoint, Instant.now().minusSeconds(1));
            }

            long started = System.nanoTime();
            deliveryWorker.poll();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            // Four receivers that each take 1s: about 4s one after another, about 1s in parallel.
            assertThat(count("select count(*) from deliveries where status = 'DELIVERED'")).isEqualTo(4);
            assertThat(elapsedMs).isLessThan(2_500);
        } finally {
            receiver.stop(0);
        }
    }

    // ---------------------------------------------------------------- claim tokens

    @Test
    void aWorkerWhoseLeaseExpiredCannotOverwriteTheWorkerThatReclaimedTheDelivery() {
        UUID endpoint = createEndpoint(true, 5);
        UUID delivery = createDueDelivery(endpoint, Instant.now().minusSeconds(1));

        ClaimedDelivery slowWorker = single(workerService.claimDue());
        expireLease(delivery);
        ClaimedDelivery newWorker = single(workerService.claimDue());

        assertThat(newWorker.deliveryId()).isEqualTo(delivery);
        assertThat(newWorker.claimToken()).isNotEqualTo(slowWorker.claimToken());

        assertThat(workerService.recordOutcome(slowWorker, success())).isFalse();
        assertThat(statusOf(delivery)).isEqualTo("PROCESSING");

        assertThat(workerService.recordOutcome(newWorker, failure(503))).isTrue();
        assertThat(statusOf(delivery)).isEqualTo("RETRY_PENDING");
        assertThat(count("select count(*) from delivery_attempts where delivery_id = ?", delivery)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- pause

    @Test
    void deliveriesForAPausedEndpointWaitInTheQueueUntilItIsTurnedBackOn() {
        UUID endpoint = createEndpoint(false, 5);
        UUID delivery = createDueDelivery(endpoint, Instant.now().minusSeconds(1));

        assertThat(workerService.claimDue()).isEmpty();

        jdbc.update("update webhook_endpoints set active = true where id = ?", endpoint);
        assertThat(single(workerService.claimDue()).deliveryId()).isEqualTo(delivery);
    }

    @Test
    void pausingAnEndpointAfterTheClaimReturnsTheDeliveryWithoutSpendingAnAttempt() {
        UUID endpoint = createEndpoint(true, 5);
        UUID delivery = createDueDelivery(endpoint, Instant.now().minusSeconds(1));
        ClaimedDelivery claim = single(workerService.claimDue());

        jdbc.update("update webhook_endpoints set active = false where id = ?", endpoint);

        assertThat(workerService.prepare(claim)).isEmpty();
        assertThat(statusOf(delivery)).isEqualTo("PENDING");
        assertThat(count("select attempt_count from deliveries where id = ?", delivery)).isZero();
        assertThat(count("select count(*) from deliveries where id = ? and claim_token is null and lease_until is null", delivery)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- endpoint health

    @Test
    void anEndpointIsPausedAutomaticallyAfterEnoughDeadDeliveriesInARow() {
        UUID endpoint = createEndpoint(true, 1);

        for (int i = 1; i <= AUTO_DISABLE_AFTER_DEAD; i++) {
            assertThat(isActive(endpoint)).as("still active before dead delivery %d", i).isTrue();
            deliverOnce(endpoint, failure(503));
        }

        assertThat(isActive(endpoint)).isFalse();
        assertThat(jdbc.queryForObject("select disabled_reason from webhook_endpoints where id = ?", String.class, endpoint))
                .contains(AUTO_DISABLE_AFTER_DEAD + " deliveries in a row failed")
                .contains("HTTP 503");
    }

    @Test
    void oneSuccessResetsTheDeadStreak() {
        UUID endpoint = createEndpoint(true, 1);
        for (int i = 1; i < AUTO_DISABLE_AFTER_DEAD; i++) {
            deliverOnce(endpoint, failure(503));
        }
        deliverOnce(endpoint, success());
        deliverOnce(endpoint, failure(503));

        assertThat(isActive(endpoint)).isTrue();
        assertThat(count("select consecutive_dead_deliveries from webhook_endpoints where id = ?", endpoint)).isEqualTo(1);
    }

    @Test
    void http410GoneKillsTheDeliveryAndPausesTheEndpointAtOnce() {
        UUID endpoint = createEndpoint(true, 5);
        UUID delivery = deliverOnce(endpoint, failure(410));

        assertThat(statusOf(delivery)).isEqualTo("DEAD");
        assertThat(count("select attempt_count from deliveries where id = ?", delivery)).isEqualTo(1);
        assertThat(isActive(endpoint)).isFalse();
        assertThat(jdbc.queryForObject("select disabled_reason from webhook_endpoints where id = ?", String.class, endpoint))
                .contains("410");
    }

    @Test
    void turningAnAutoPausedEndpointBackOnClearsItsHealthRecord() throws Exception {
        UUID endpoint = createEndpoint(true, 5);
        deliverOnce(endpoint, failure(410));

        mockMvc.perform(patch("/api/v1/endpoints/{id}", endpoint)
                        .header("X-API-Key", API_KEY)
                        .contentType("application/json")
                        .content("""
                                {"name":"Recovered","url":"https://example.com/hook","maxAttempts":5,"active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.disabledReason").doesNotExist())
                .andExpect(jsonPath("$.consecutiveDeadDeliveries").value(0));
    }

    // ---------------------------------------------------------------- helpers

    private UUID deliverOnce(UUID endpoint, DeliveryResult result) {
        UUID delivery = createDueDelivery(endpoint, Instant.now().minusSeconds(1));
        ClaimedDelivery claim = single(workerService.claimDue());
        assertThat(claim.deliveryId()).isEqualTo(delivery);
        assertThat(workerService.prepare(claim)).isPresent();
        assertThat(workerService.recordOutcome(claim, result)).isTrue();
        return delivery;
    }

    private UUID createEndpoint(boolean active, int maxAttempts) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into webhook_endpoints (id, name, url, signing_secret, active, max_attempts, created_at, updated_at)
                values (?, ?, 'https://example.com/hook', 'test-secret', ?, ?, ?, ?)
                """, id, "test-" + id, active, maxAttempts, now, now);
        return id;
    }

    private UUID createDueDelivery(UUID endpointId, Instant due) {
        UUID eventId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        Timestamp dueAt = Timestamp.from(due);
        jdbc.update("""
                insert into events (id, event_type, payload, idempotency_key, created_at)
                values (?, 'order.created', cast('{"orderId":"1"}' as jsonb), ?, ?)
                """, eventId, "test-" + eventId, dueAt);
        int maxAttempts = count("select max_attempts from webhook_endpoints where id = ?", endpointId);
        jdbc.update("""
                insert into deliveries (id, event_id, endpoint_id, status, attempt_count, max_attempts, next_retry_at, created_at, updated_at)
                values (?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
                """, deliveryId, eventId, endpointId, maxAttempts, dueAt, dueAt, dueAt);
        return deliveryId;
    }

    private void expireLease(UUID deliveryId) {
        jdbc.update("update deliveries set lease_until = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), deliveryId);
    }

    private UUID endpointOf(UUID deliveryId) {
        return jdbc.queryForObject("select endpoint_id from deliveries where id = ?", UUID.class, deliveryId);
    }

    private String statusOf(UUID deliveryId) {
        return jdbc.queryForObject("select status from deliveries where id = ?", String.class, deliveryId);
    }

    private boolean isActive(UUID endpointId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select active from webhook_endpoints where id = ?", Boolean.class, endpointId));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static ClaimedDelivery single(List<ClaimedDelivery> claimed) {
        assertThat(claimed).hasSize(1);
        return claimed.getFirst();
    }

    private static DeliveryResult success() {
        Instant now = Instant.now();
        return new DeliveryResult(now, now, 200, "ok", null, 1);
    }

    private static DeliveryResult failure(int httpStatus) {
        Instant now = Instant.now();
        return new DeliveryResult(now, now, httpStatus, "nope", null, 1);
    }
}
