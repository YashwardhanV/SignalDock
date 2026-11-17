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

    /** HTTP 410 Gone: the receiver says this URL will never accept webhooks again, so retrying is pointless. */
    public boolean gone() {
        return httpStatus != null && httpStatus == 410;
    }

    public String failureSummary() {
        if (errorMessage != null && !errorMessage.isBlank()) {
            return errorMessage;
        }
        return httpStatus == null ? "Delivery failed" : "HTTP " + httpStatus;
    }
}

