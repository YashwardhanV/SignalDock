package dev.signaldock.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.signaldock.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryProcessingService {
    private final AppProperties properties;
    private final Clock clock;
    private final DeliveryAttemptRepository attemptRepository;
    private final DeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final RetryPolicy retryPolicy;

    public DeliveryProcessingService(
            AppProperties properties,
            Clock clock,
            DeliveryAttemptRepository attemptRepository,
            DeliveryRepository deliveryRepository,
            ObjectMapper objectMapper,
            RetryPolicy retryPolicy
    ) {
        this.properties = properties;
        this.clock = clock;
        this.attemptRepository = attemptRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
        this.retryPolicy = retryPolicy;
    }

    @Transactional(readOnly = true)
    public Optional<DeliveryHttpClient.DeliveryWorkItem> prepare(UUID deliveryId) {
        return deliveryRepository.findDetailedById(deliveryId)
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.PROCESSING)
                .map(delivery -> new DeliveryHttpClient.DeliveryWorkItem(
                        delivery.getId(),
                        delivery.getEvent().getId(),
                        delivery.getEvent().getEventType(),
                        rawPayload(delivery),
                        delivery.getEndpoint().getUrl(),
                        delivery.getEndpoint().getSigningSecret()
                ));
    }

    @Transactional
    public void recordOutcome(UUID deliveryId, DeliveryResult result) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != DeliveryStatus.PROCESSING) {
            return;
        }

        int attemptNumber = delivery.getAttemptCount() + 1;
        attemptRepository.save(new DeliveryAttempt(
                delivery,
                attemptNumber,
                result,
                properties.delivery().responseBodyLimit()
        ));

        if (result.successful()) {
            delivery.markDelivered(result.finishedAt());
            return;
        }

        boolean exhausted = attemptNumber >= delivery.getMaxAttempts();
        Instant next = exhausted
                ? clock.instant()
                : clock.instant().plus(retryPolicy.delayAfterAttempt(attemptNumber));
        delivery.markFailedAttempt(result.failureSummary(), next, exhausted);
    }

    private String rawPayload(Delivery delivery) {
        try {
            return objectMapper.writeValueAsString(delivery.getEvent().getPayload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored event payload could not be serialized", exception);
        }
    }
}

