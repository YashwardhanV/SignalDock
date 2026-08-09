package dev.signaldock.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DeliveryDtos {
    private DeliveryDtos() {
    }

    public record Response(
            UUID id,
            UUID eventId,
            String eventType,
            UUID endpointId,
            String endpointName,
            String endpointUrl,
            DeliveryStatus status,
            int attemptCount,
            int maxAttempts,
            Instant nextRetryAt,
            String lastError,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(Delivery delivery) {
            return new Response(
                    delivery.getId(),
                    delivery.getEvent().getId(),
                    delivery.getEvent().getEventType(),
                    delivery.getEndpoint().getId(),
                    delivery.getEndpoint().getName(),
                    delivery.getEndpoint().getUrl(),
                    delivery.getStatus(),
                    delivery.getAttemptCount(),
                    delivery.getMaxAttempts(),
                    delivery.getNextRetryAt(),
                    delivery.getLastError(),
                    delivery.getCompletedAt(),
                    delivery.getCreatedAt(),
                    delivery.getUpdatedAt()
            );
        }
    }

    public record AttemptResponse(
            UUID id,
            int attemptNumber,
            Instant startedAt,
            Instant finishedAt,
            Integer httpStatus,
            String responseBody,
            String errorMessage,
            long latencyMs
    ) {
        public static AttemptResponse from(DeliveryAttempt attempt) {
            return new AttemptResponse(
                    attempt.getId(),
                    attempt.getAttemptNumber(),
                    attempt.getStartedAt(),
                    attempt.getFinishedAt(),
                    attempt.getHttpStatus(),
                    attempt.getResponseBody(),
                    attempt.getErrorMessage(),
                    attempt.getLatencyMs()
            );
        }
    }

    public record DetailResponse(Response delivery, List<AttemptResponse> attempts) {
    }

    public record DashboardSummary(
            long total,
            Map<DeliveryStatus, Long> byStatus,
            double successRate
    ) {
    }
}

