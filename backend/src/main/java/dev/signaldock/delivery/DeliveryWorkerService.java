package dev.signaldock.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.signaldock.config.AppProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryWorkerService {
    private final AppProperties properties;
    private final DeliveryAttemptRepository attemptRepository;
    private final DeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final RetryPolicy retryPolicy;

    public DeliveryWorkerService(
            AppProperties properties,
            DeliveryAttemptRepository attemptRepository,
            DeliveryRepository deliveryRepository,
            ObjectMapper objectMapper,
            RetryPolicy retryPolicy
    ) {
        this.properties = properties;
        this.attemptRepository = attemptRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public List<UUID> claimDue() {
        Instant now = Instant.now();
        List<Delivery> due = deliveryRepository.lockDueBatch(now, properties.delivery().batchSize());
        due.forEach(delivery -> delivery.claim(now, properties.delivery().leaseDuration()));
        return due.stream().map(Delivery::getId).toList();
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
        attemptRepository.save(new DeliveryAttempt(delivery, attemptNumber, result));

        if (result.successful()) {
            delivery.markDelivered(result.finishedAt());
            return;
        }

        boolean exhausted = attemptNumber >= delivery.getMaxAttempts();
        Instant next = exhausted
                ? Instant.now()
                : Instant.now().plus(retryPolicy.delayAfterAttempt(attemptNumber));
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
