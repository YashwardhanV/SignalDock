package dev.signaldock.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.signaldock.delivery.DeliveryRepository;
import dev.signaldock.endpoint.EndpointRepository;
import dev.signaldock.endpoint.WebhookEndpoint;
import dev.signaldock.event.EventRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest
class ApiIntegrationTest {
    private static final String API_KEY = "integration-api-key";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.demo.enabled", () -> "true");
        registry.add("app.api-key", () -> API_KEY);
        registry.add("app.demo.receiver-url", () -> "http://127.0.0.1:65530/success");
        registry.add("app.demo.failure-url", () -> "http://127.0.0.1:65530/failure");
        registry.add("app.delivery.poll-delay", () -> "3600000");
        registry.add("app.delivery.allow-http", () -> "true");
        registry.add("app.delivery.allow-private-networks", () -> "true");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EventRepository eventRepository;
    @Autowired DeliveryRepository deliveryRepository;
    @Autowired EndpointRepository endpointRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void rejectsRequestsWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    @Transactional
    void idempotentIngestionCreatesOneEventAndOneDelivery() throws Exception {
        long eventsBefore = eventRepository.count();
        long deliveriesBefore = deliveryRepository.count();
        String key = "integration-" + UUID.randomUUID();
        String body = """
                {"eventType":"order.created","payload":{"orderId":"ord_123"}}
                """;

        String firstBody = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", API_KEY)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.deliveriesCreated").value(1))
                .andReturn().getResponse().getContentAsString();

        String secondBody = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", API_KEY)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.deliveriesCreated").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode first = objectMapper.readTree(firstBody);
        JsonNode second = objectMapper.readTree(secondBody);
        assertThat(second.get("eventId").asText()).isEqualTo(first.get("eventId").asText());
        assertThat(eventRepository.count()).isEqualTo(eventsBefore + 1);
        assertThat(deliveryRepository.count()).isEqualTo(deliveriesBefore + 1);
    }

    @Test
    @Transactional
    void databaseRejectsDuplicateActiveEndpointUrls() {
        String url = "https://duplicate-" + UUID.randomUUID() + ".example.com/callback";
        endpointRepository.saveAndFlush(new WebhookEndpoint("First", url, "secret-1", 3));

        assertThatThrownBy(() -> endpointRepository.saveAndFlush(new WebhookEndpoint("Second", url, "secret-2", 3)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void manualRetryRequeuesDeadDeliveryAndAddsAttemptBudget() throws Exception {
        UUID endpointId = endpointRepository.findAll().stream()
                .filter(endpoint -> endpoint.getMaxAttempts() == 5)
                .findFirst()
                .orElseThrow()
                .getId();
        UUID eventId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into events (id,event_type,payload,idempotency_key,created_at) values (?,?,cast(? as jsonb),?,?)",
                eventId, "manual.retry", "{}", "manual-" + eventId, Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
                """
                insert into deliveries
                    (id,event_id,endpoint_id,status,attempt_count,max_attempts,next_retry_at,last_error,completed_at,created_at,updated_at,version)
                values (?,?,?,'DEAD',3,3,?,'HTTP 503',?,?,?,0)
                """,
                deliveryId, eventId, endpointId,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now())
        );

        mockMvc.perform(post("/api/v1/deliveries/{id}/retry", deliveryId)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETRY_PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(3))
                .andExpect(jsonPath("$.maxAttempts").value(8));
    }
}
