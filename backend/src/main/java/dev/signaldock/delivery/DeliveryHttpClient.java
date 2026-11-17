package dev.signaldock.delivery;

import dev.signaldock.config.AppProperties;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class DeliveryHttpClient {
    /** Only this much of a receiver's response is kept. The rest is never read into memory. */
    static final int MAX_RESPONSE_BYTES = DeliveryAttempt.MAX_TEXT_LENGTH - 32;
    static final String TRUNCATION_MARKER = "...[truncated]";

    private final AppProperties properties;
    private final HmacSignatureService signatureService;
    private final HttpClient httpClient;

    public DeliveryHttpClient(AppProperties properties, HmacSignatureService signatureService) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.delivery().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DeliveryResult deliver(DeliveryWorkItem item) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        String timestamp = Long.toString(startedAt.getEpochSecond());
        String signature = signatureService.sign(item.signingSecret(), timestamp, item.rawPayload());
        // HttpRequest.timeout only covers waiting for the response headers. A receiver that sends
        // headers and then trickles the body one byte at a time would otherwise hold this thread
        // forever, so the whole exchange also gets one hard deadline.
        Duration deadline = properties.delivery().maxSendDuration();

        CompletableFuture<HttpResponse<String>> future = null;
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
            future = httpClient.sendAsync(request, responseInfo -> new CappedBodySubscriber(MAX_RESPONSE_BYTES));
            HttpResponse<String> response = future.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            return result(startedAt, response.statusCode(), response.body(), null, startedNanos);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return result(startedAt, null, null,
                    "HttpTimeoutException: no complete response within " + deadline.toMillis() + " ms", startedNanos);
        } catch (InterruptedException exception) {
            if (future != null) {
                future.cancel(true);
            }
            Thread.currentThread().interrupt();
            return result(startedAt, null, null, "Delivery interrupted", startedNanos);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return result(startedAt, null, null, describe(cause), startedNanos);
        } catch (Exception exception) {
            return result(startedAt, null, null, describe(exception), startedNanos);
        }
    }

    private static String describe(Throwable throwable) {
        return throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
    }

    private DeliveryResult result(
            Instant startedAt,
            Integer status,
            String body,
            String error,
            long startedNanos
    ) {
        long latencyMs = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
        return new DeliveryResult(startedAt, Instant.now(), status, body, error, latencyMs);
    }

    public record DeliveryWorkItem(
            UUID deliveryId,
            UUID eventId,
            String eventType,
            String rawPayload,
            String url,
            String signingSecret
    ) {
    }

    /**
     * Reads at most {@code maxBytes} of the response body, then cancels the stream. A receiver that
     * answers with a 1 GB body costs us 4 KB of memory, not 1 GB.
     */
    static final class CappedBodySubscriber implements HttpResponse.BodySubscriber<String> {
        private final int maxBytes;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        CappedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<String> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            boolean truncated = false;
            for (ByteBuffer item : items) {
                int take = Math.min(maxBytes - buffer.size(), item.remaining());
                byte[] chunk = new byte[take];
                item.get(chunk);
                buffer.write(chunk, 0, take);
                truncated |= item.hasRemaining();
            }
            if (truncated || buffer.size() >= maxBytes) {
                subscription.cancel();
                finish(truncated);
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            finish(false);
        }

        private void finish(boolean truncated) {
            String text = buffer.toString(StandardCharsets.UTF_8);
            body.complete(truncated ? text + TRUNCATION_MARKER : text);
        }
    }
}
