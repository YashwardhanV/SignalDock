package dev.signaldock.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.signaldock.config.PageResponse;
import dev.signaldock.delivery.Delivery;
import dev.signaldock.delivery.DeliveryRepository;
import dev.signaldock.exception.InvalidRequestException;
import dev.signaldock.exception.ResourceNotFoundException;
import dev.signaldock.subscription.EventPatternMatcher;
import dev.signaldock.subscription.SubscriptionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
    private final DeliveryRepository deliveryRepository;
    private final EventPatternMatcher matcher;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final SubscriptionRepository subscriptionRepository;

    public EventService(
            DeliveryRepository deliveryRepository,
            EventPatternMatcher matcher,
            EventRepository eventRepository,
            ObjectMapper objectMapper,
            SubscriptionRepository subscriptionRepository
    ) {
        this.deliveryRepository = deliveryRepository;
        this.matcher = matcher;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public EventDtos.IngestionResponse ingest(String rawIdempotencyKey, EventDtos.CreateRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        int inserted = eventRepository.insertIfAbsent(
                eventId,
                request.eventType().trim(),
                toJson(request),
                idempotencyKey,
                now
        );

        if (inserted == 0) {
            Event existing = eventRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotent event disappeared after conflict"));
            return response(existing, 0, true);
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Inserted event could not be loaded"));
        LinkedHashMap<UUID, dev.signaldock.endpoint.WebhookEndpoint> matchedEndpoints = new LinkedHashMap<>();
        subscriptionRepository.findActiveWithActiveEndpoints().stream()
                .filter(subscription -> matcher.matches(subscription.getEventPattern(), event.getEventType()))
                .forEach(subscription -> matchedEndpoints.putIfAbsent(
                        subscription.getEndpoint().getId(),
                        subscription.getEndpoint()
                ));

        var deliveries = matchedEndpoints.values().stream()
                .map(endpoint -> new Delivery(event, endpoint, now))
                .toList();
        deliveryRepository.saveAll(deliveries);
        return response(event, deliveries.size(), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventDtos.Response> list(int page, int size) {
        return PageResponse.from(
                eventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)),
                EventDtos.Response::from
        );
    }

    @Transactional(readOnly = true)
    public EventDtos.Response get(UUID eventId) {
        return eventRepository.findById(eventId)
                .map(EventDtos.Response::from)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
    }

    private String normalizeIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("Idempotency-Key header is required.");
        }
        String normalized = raw.trim();
        if (normalized.length() > 180) {
            throw new InvalidRequestException("Idempotency-Key must be at most 180 characters.");
        }
        return normalized;
    }

    private String toJson(EventDtos.CreateRequest request) {
        try {
            return objectMapper.writeValueAsString(request.payload());
        } catch (JsonProcessingException exception) {
            throw new InvalidRequestException("Payload could not be serialized.");
        }
    }

    private EventDtos.IngestionResponse response(Event event, int deliveries, boolean duplicate) {
        return new EventDtos.IngestionResponse(
                event.getId(),
                event.getEventType(),
                deliveries,
                duplicate,
                event.getCreatedAt()
        );
    }
}

