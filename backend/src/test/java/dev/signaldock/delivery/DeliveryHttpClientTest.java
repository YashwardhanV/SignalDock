package dev.signaldock.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.signaldock.config.AppProperties;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DeliveryHttpClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsSignedHeadersAndRecordsSuccess() throws Exception {
        AtomicReference<String> signature = new AtomicReference<>();
        AtomicReference<String> deliveryId = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/callback", exchange -> {
            signature.set(exchange.getRequestHeaders().getFirst("X-SignalDock-Signature"));
            deliveryId.set(exchange.getRequestHeaders().getFirst("X-SignalDock-Delivery-Id"));
            byte[] response = "accepted".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        DeliveryHttpClient client = client(Duration.ofSeconds(1));
        DeliveryHttpClient.DeliveryWorkItem item = item("/callback");
        DeliveryResult result = client.deliver(item);

        assertThat(result.successful()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(202);
        assertThat(result.responseBody()).isEqualTo("accepted");
        assertThat(signature.get()).startsWith("sha256=");
        assertThat(deliveryId.get()).isEqualTo(item.deliveryId().toString());
    }

    @Test
    void turnsReadTimeoutIntoARecordedFailure() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(250);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        DeliveryResult result = client(Duration.ofMillis(50)).deliver(item("/slow"));

        assertThat(result.successful()).isFalse();
        assertThat(result.httpStatus()).isNull();
        assertThat(result.errorMessage()).containsIgnoringCase("timeout");
    }

    @Test
    void keepsOnlyTheFirstFewKilobytesOfAHugeResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/huge", exchange -> {
            byte[] chunk = "x".repeat(64 * 1024).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, 0); // chunked, 16 MB in total
            try (var body = exchange.getResponseBody()) {
                for (int i = 0; i < 256; i++) {
                    body.write(chunk);
                }
            } catch (java.io.IOException expectedWhenClientStopsReading) {
                // The client cancels after a few kilobytes and closes the connection.
            } finally {
                exchange.close();
            }
        });
        server.start();

        DeliveryResult result = client(Duration.ofSeconds(2)).deliver(item("/huge"));

        assertThat(result.successful()).isTrue();
        assertThat(result.responseBody())
                .hasSize(DeliveryHttpClient.MAX_RESPONSE_BYTES + DeliveryHttpClient.TRUNCATION_MARKER.length())
                .endsWith(DeliveryHttpClient.TRUNCATION_MARKER);
    }

    @Test
    void aReceiverThatTricklesItsBodyIsCutOffAtTheDeadline() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/trickle", exchange -> {
            exchange.sendResponseHeaders(200, 0); // headers arrive at once, so HttpRequest.timeout never fires
            try (var body = exchange.getResponseBody()) {
                for (int i = 0; i < 100; i++) {
                    body.write('x');
                    body.flush();
                    Thread.sleep(100);
                }
            } catch (java.io.IOException | InterruptedException expectedWhenClientGivesUp) {
                // The client cancels the exchange at its deadline.
            } finally {
                exchange.close();
            }
        });
        server.start();

        // connect-timeout 1s + read-timeout 300ms = 1.3s hard deadline, while the body would take 10s.
        DeliveryResult result = client(Duration.ofMillis(300)).deliver(item("/trickle"));

        assertThat(result.successful()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("timeout");
        assertThat(result.latencyMs()).isLessThan(4_000);
    }

    @Test
    void recognisesHttp410AsGone() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/gone", exchange -> {
            exchange.sendResponseHeaders(410, -1);
            exchange.close();
        });
        server.start();

        DeliveryResult result = client(Duration.ofSeconds(1)).deliver(item("/gone"));

        assertThat(result.successful()).isFalse();
        assertThat(result.gone()).isTrue();
        assertThat(result.failureSummary()).isEqualTo("HTTP 410");
    }

    private DeliveryHttpClient client(Duration readTimeout) {
        AppProperties properties = new AppProperties(
                "admin",
                List.of(),
                new AppProperties.Delivery(
                        Duration.ofSeconds(1),
                        readTimeout,
                        10,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(1),
                        Duration.ofMinutes(1),
                        0.2,
                        2,
                        5
                ),
                new AppProperties.Demo(false, "", "")
        );
        return new DeliveryHttpClient(properties, new HmacSignatureService());
    }

    private DeliveryHttpClient.DeliveryWorkItem item(String path) {
        return new DeliveryHttpClient.DeliveryWorkItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "order.created",
                "{\"orderId\":\"ord_123\"}",
                "http://127.0.0.1:" + server.getAddress().getPort() + path,
                "secret"
        );
    }
}
