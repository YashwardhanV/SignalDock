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
            Duration retryMaxDelay
    ) {
    }

    public record Demo(
            boolean enabled,
            String receiverUrl,
            String failureUrl
    ) {
    }
}

