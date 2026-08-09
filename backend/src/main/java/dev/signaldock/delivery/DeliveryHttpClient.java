package dev.signaldock.delivery;

import dev.signaldock.config.AppProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class DeliveryHttpClient {
    private final AppProperties properties;
    private final Clock clock;
    private final HmacSignatureService signatureService;
    private final HttpClient httpClient;

    public DeliveryHttpClient(AppProperties properties, Clock clock, HmacSignatureService signatureService) {
        this.properties = properties;
        this.clock = clock;
        this.signatureService = signatureService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.delivery().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DeliveryResult deliver(DeliveryWorkItem item) {
        Instant startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        String timestamp = Long.toString(startedAt.getEpochSecond());
        String signature = signatureService.sign(item.signingSecret(), timestamp, item.rawPayload());

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(item.url()))
                    .timeout(properties.delivery().readTimeout())
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SignalDock/1.0")
                    .header("X-SignalDock-Event-Id", item.eventId().toString())
                    .header("X-SignalDock-Event-Type", item.eventType())
                    .header("X-SignalDock-Delivery-Id", item.deliveryId().toString())
                    .header("X-SignalDock-Timestamp", timestamp)
                    .header("X-SignalDock-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(item.rawPayload(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return result(startedAt, response.statusCode(), response.body(), null, startedNanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return result(startedAt, null, null, "Delivery interrupted", startedNanos);
        } catch (Exception exception) {
            String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            return result(startedAt, null, null, message, startedNanos);
        }
    }

    private DeliveryResult result(
            Instant startedAt,
            Integer status,
            String body,
            String error,
            long startedNanos
    ) {
        long latencyMs = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
        return new DeliveryResult(startedAt, clock.instant(), status, body, error, latencyMs);
    }

    public record DeliveryWorkItem(
            java.util.UUID deliveryId,
            java.util.UUID eventId,
            String eventType,
            String rawPayload,
            String url,
            String signingSecret
    ) {
    }
}
