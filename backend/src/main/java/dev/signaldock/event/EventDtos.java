package dev.signaldock.event;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class EventDtos {
    private EventDtos() {
    }

    public record CreateRequest(
            @NotBlank
            @Size(max = 160)
            @Pattern(regexp = "^[a-zA-Z0-9_-]+(?:\\.[a-zA-Z0-9_-]+)*$", message = "must use dot-separated words")
            String eventType,
            @NotNull JsonNode payload
    ) {
    }

    public record IngestionResponse(
            UUID eventId,
            String eventType,
            int deliveriesCreated,
            boolean duplicate,
            Instant createdAt
    ) {
    }

    public record Response(
            UUID id,
            String eventType,
            JsonNode payload,
            String idempotencyKey,
            Instant createdAt
    ) {
        public static Response from(Event event) {
            return new Response(
                    event.getId(),
                    event.getEventType(),
                    event.getPayload(),
                    event.getIdempotencyKey(),
                    event.getCreatedAt()
            );
        }
    }
}

