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

    private DeliveryHttpClient client(Duration readTimeout) {
        AppProperties properties = new AppProperties(
                "admin",
                List.of(),
                new AppProperties.Delivery(
                        Duration.ofSeconds(1),
                        readTimeout,
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(1),
                        Duration.ofMinutes(1)
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
