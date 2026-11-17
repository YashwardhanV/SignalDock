package dev.signaldock.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.signaldock.delivery.DeliveryRepository;
import dev.signaldock.endpoint.EndpointRepository;
import dev.signaldock.event.EventRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

class ApiIntegrationTest extends PostgresIntegrationTest {
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
    void manualRetryRequeuesDeadDeliveryAndAddsAttemptBudget() throws Exception {
        UUID endpointId = endpointRepository.findAll().stream()
                .filter(endpoint -> endpoint.getName().equals("Demo receiver"))
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
                    (id,event_id,endpoint_id,status,attempt_count,max_attempts,next_retry_at,last_error,completed_at,created_at,updated_at)
                values (?,?,?,'DEAD',3,3,?,'HTTP 503',?,?,?)
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

    @Test
    @Transactional
    void reusingAnIdempotencyKeyForADifferentRequestIsRejected() throws Exception {
        String key = "reuse-" + UUID.randomUUID();
        sendEvent(key, """
                {"eventType":"order.created","payload":{"orderId":"ord_1","amount":10}}
                """).andExpect(status().isCreated());

        sendEvent(key, """
                {"eventType":"order.created","payload":{"orderId":"ord_2","amount":10}}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));

        sendEvent(key, """
                {"eventType":"order.cancelled","payload":{"orderId":"ord_1","amount":10}}
                """).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Transactional
    void aRetryWithTheSameJsonInADifferentShapeIsStillADuplicate() throws Exception {
        String key = "same-" + UUID.randomUUID();
        sendEvent(key, """
                {"eventType":"order.created","payload":{"orderId":"ord_1","amount":10}}
                """).andExpect(status().isCreated());

        // Different key order, whitespace and number formatting, same JSON value.
        sendEvent(key, """
                {"eventType":"order.created","payload":{ "amount" : 10.0, "orderId" : "ord_1" }}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    private org.springframework.test.web.servlet.ResultActions sendEvent(String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/events")
                .header("X-API-Key", API_KEY)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body));
    }
}
