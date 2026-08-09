package dev.signaldock.event;

import dev.signaldock.config.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventIngestionService ingestionService;
    private final EventRepository eventRepository;

    public EventController(EventIngestionService ingestionService, EventRepository eventRepository) {
        this.ingestionService = ingestionService;
        this.eventRepository = eventRepository;
    }

    @PostMapping
    ResponseEntity<EventDtos.IngestionResponse> ingest(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody EventDtos.CreateRequest request
    ) {
        EventDtos.IngestionResponse response = ingestionService.ingest(idempotencyKey, request);
        if (response.duplicate()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create("/api/v1/events/" + response.eventId())).body(response);
    }

    @GetMapping
    PageResponse<EventDtos.Response> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                eventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)),
                EventDtos.Response::from
        );
    }

    @GetMapping("/{eventId}")
    EventDtos.Response get(@PathVariable UUID eventId) {
        return ingestionService.get(eventId);
    }
}

