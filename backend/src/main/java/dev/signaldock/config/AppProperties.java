package dev.signaldock.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String adminKey,
        List<String> corsAllowedOrigins,
        Delivery delivery,
        Demo demo
) {
    public record Delivery(
            Duration connectTimeout,
            Duration readTimeout,
            int batchSize,
            Duration pollDelay,
            Duration leaseDuration,
            Duration retryBaseDelay,
            Duration retryMaxDelay,
            int responseBodyLimit,
            boolean allowHttp,
            boolean allowPrivateNetworks
    ) {
    }

    public record Demo(
            boolean enabled,
            String apiKey,
            String receiverUrl,
            String failureUrl
    ) {
    }
}

