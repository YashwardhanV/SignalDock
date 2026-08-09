package dev.signaldock.delivery;

import java.time.Instant;

public record DeliveryResult(
        Instant startedAt,
        Instant finishedAt,
        Integer httpStatus,
        String responseBody,
        String errorMessage,
        long latencyMs
) {
    public boolean successful() {
        return httpStatus != null && httpStatus >= 200 && httpStatus < 300;
    }

    public String failureSummary() {
        if (errorMessage != null && !errorMessage.isBlank()) {
            return errorMessage;
        }
        return httpStatus == null ? "Delivery failed" : "HTTP " + httpStatus;
    }
}

