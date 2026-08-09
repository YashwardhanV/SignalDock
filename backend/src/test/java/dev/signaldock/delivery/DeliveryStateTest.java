package dev.signaldock.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.signaldock.endpoint.WebhookEndpoint;
import dev.signaldock.event.Event;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryStateTest {
    private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");

    @Test
    void failedAttemptMovesToRetryPendingBeforeBudgetIsExhausted() {
        Delivery delivery = delivery(3);
        delivery.claim("worker-1", NOW, Duration.ofSeconds(30));

        delivery.markFailedAttempt("HTTP 503", NOW.plusSeconds(30), false);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.RETRY_PENDING);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextRetryAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(delivery.getClaimedBy()).isNull();
    }

    @Test
    void exhaustedAttemptMovesToDeadState() {
        Delivery delivery = delivery(1);
        delivery.claim("worker-1", NOW, Duration.ofSeconds(30));

        delivery.markFailedAttempt("timeout", NOW, true);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void manualRetryAddsACompleteAttemptBudgetWithoutErasingHistory() {
        Delivery delivery = delivery(2);
        delivery.claim("worker-1", NOW, Duration.ofSeconds(30));
        delivery.markFailedAttempt("HTTP 503", NOW, false);
        delivery.claim("worker-1", NOW, Duration.ofSeconds(30));
        delivery.markFailedAttempt("HTTP 503", NOW, true);

        delivery.requeue(2, NOW.plusSeconds(1));

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.RETRY_PENDING);
        assertThat(delivery.getAttemptCount()).isEqualTo(2);
        assertThat(delivery.getMaxAttempts()).isEqualTo(4);
    }

    private Delivery delivery(int maxAttempts) {
        return new Delivery(
                new Event(
                        java.util.UUID.randomUUID(),
                        "order.created",
                        new ObjectMapper().createObjectNode().put("orderId", "ord_123"),
                        "key-" + java.util.UUID.randomUUID(),
                        NOW
                ),
                new WebhookEndpoint("Receiver", "https://example.com/callback", "secret", maxAttempts),
                NOW
        );
    }
}
