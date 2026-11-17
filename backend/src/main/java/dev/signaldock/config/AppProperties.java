package dev.signaldock.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String apiKey,
        List<String> corsAllowedOrigins,
        Delivery delivery,
        Demo demo
) {
    public record Delivery(
            Duration connectTimeout,
            Duration readTimeout,
            int batchSize,
            Duration leaseDuration,
            Duration retryBaseDelay,
            Duration retryMaxDelay,
            double retryJitter,
            int maxInFlightPerEndpoint,
            int autoDisableAfterDeadDeliveries
    ) {
        public Delivery {
            if (batchSize < 1) {
                throw new IllegalArgumentException("app.delivery.batch-size must be at least 1");
            }
            if (maxInFlightPerEndpoint < 1) {
                throw new IllegalArgumentException("app.delivery.max-in-flight-per-endpoint must be at least 1");
            }
            if (retryJitter < 0 || retryJitter >= 1) {
                throw new IllegalArgumentException("app.delivery.retry-jitter must be in [0, 1)");
            }
            if (autoDisableAfterDeadDeliveries < 0) {
                throw new IllegalArgumentException("app.delivery.auto-disable-after-dead-deliveries must be 0 (off) or more");
            }
            // A lease shorter than the longest possible send lets another worker reclaim a delivery
            // that is still in flight, which turns "at least once" into "usually twice".
            // (Inside a compact constructor the fields are not assigned yet, so use the parameters.)
            Duration longestSend = connectTimeout.plus(readTimeout);
            if (leaseDuration.compareTo(longestSend) <= 0) {
                throw new IllegalArgumentException(
                        "app.delivery.lease-duration (" + leaseDuration + ") must be longer than connect-timeout + read-timeout ("
                                + longestSend + ")");
            }
        }

        /** Hard upper bound for one HTTP attempt: connecting plus receiving the (size-capped) response. */
        public Duration maxSendDuration() {
            return connectTimeout.plus(readTimeout);
        }
    }

    public record Demo(
            boolean enabled,
            String receiverUrl,
            String failureUrl
    ) {
    }
}
