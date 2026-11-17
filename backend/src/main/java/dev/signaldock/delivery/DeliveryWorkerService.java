package dev.signaldock.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.signaldock.config.AppProperties;
import dev.signaldock.endpoint.EndpointRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryWorkerService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorkerService.class);

    private final AppProperties properties;
    private final DeliveryAttemptRepository attemptRepository;
    private final DeliveryRepository deliveryRepository;
    private final EndpointRepository endpointRepository;
    private final ObjectMapper objectMapper;
    private final RetryPolicy retryPolicy;

    public DeliveryWorkerService(
            AppProperties properties,
            DeliveryAttemptRepository attemptRepository,
            DeliveryRepository deliveryRepository,
            EndpointRepository endpointRepository,
            ObjectMapper objectMapper,
            RetryPolicy retryPolicy
    ) {
        this.properties = properties;
        this.attemptRepository = attemptRepository;
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.objectMapper = objectMapper;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public List<ClaimedDelivery> claimDue() {
        Instant now = Instant.now();
        AppProperties.Delivery config = properties.delivery();
        List<Delivery> due = deliveryRepository.lockDueBatch(now, config.batchSize(), config.maxInFlightPerEndpoint());
        return due.stream()
                .map(delivery -> new ClaimedDelivery(delivery.getId(), delivery.claim(now, config.leaseDuration())))
                .toList();
    }

    /**
     * Loads what the HTTP call needs. Returns empty if the claim is no longer ours, or if the endpoint
     * was paused after the claim; in that case the delivery goes back to the queue untouched, so it
     * is sent when the endpoint is turned back on.
     */
    @Transactional
    public Optional<DeliveryHttpClient.DeliveryWorkItem> prepare(ClaimedDelivery claim) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(claim.deliveryId()).orElse(null);
        if (delivery == null || !delivery.isClaimedBy(claim.claimToken())) {
            return Optional.empty();
        }
        if (!delivery.getEndpoint().isActive()) {
            delivery.release(Instant.now());
            return Optional.empty();
        }
        return Optional.of(new DeliveryHttpClient.DeliveryWorkItem(
                delivery.getId(),
                delivery.getEvent().getId(),
                delivery.getEvent().getEventType(),
                rawPayload(delivery),
                delivery.getEndpoint().getUrl(),
                delivery.getEndpoint().getSigningSecret()
        ));
    }

    /**
     * Saves the attempt and moves the delivery on. If the claim token no longer matches (our lease
     * expired and another worker reclaimed the delivery), the outcome is dropped: the current owner
     * will record its own attempt. The receiver may have seen both requests, which is why delivery
     * is at least once and receivers deduplicate on X-SignalDock-Delivery-Id.
     */
    @Transactional
    public boolean recordOutcome(ClaimedDelivery claim, DeliveryResult result) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(claim.deliveryId()).orElse(null);
        if (delivery == null || !delivery.isClaimedBy(claim.claimToken())) {
            log.warn("Dropping outcome for delivery {}: claim {} is no longer current", claim.deliveryId(), claim.claimToken());
            return false;
        }

        int attemptNumber = delivery.getAttemptCount() + 1;
        attemptRepository.save(new DeliveryAttempt(delivery, attemptNumber, result));
        UUID endpointId = delivery.getEndpoint().getId();
        Instant now = Instant.now();

        if (result.successful()) {
            delivery.markDelivered(result.finishedAt());
            endpointRepository.resetDeadStreak(endpointId);
            return true;
        }

        boolean exhausted = result.gone() || attemptNumber >= delivery.getMaxAttempts();
        Instant next = exhausted ? now : now.plus(retryPolicy.delayAfterAttempt(attemptNumber));
        delivery.markFailedAttempt(result.failureSummary(), next, exhausted);
        if (exhausted) {
            updateEndpointHealthAfterDeadDelivery(endpointId, result, now);
        }
        return true;
    }

    private void updateEndpointHealthAfterDeadDelivery(UUID endpointId, DeliveryResult result, Instant now) {
        if (result.gone()) {
            if (endpointRepository.disable(endpointId, "Paused automatically: the receiver answered HTTP 410 Gone.", now) > 0) {
                log.warn("Endpoint {} paused automatically after HTTP 410 Gone", endpointId);
            }
            return;
        }
        endpointRepository.incrementDeadStreak(endpointId);
        int threshold = properties.delivery().autoDisableAfterDeadDeliveries();
        if (threshold == 0) {
            return;
        }
        String reason = "Paused automatically: " + threshold + " deliveries in a row failed (last error: "
                + abbreviate(result.failureSummary(), 200) + ").";
        if (endpointRepository.disableIfDeadStreakReached(endpointId, threshold, reason, now) > 0) {
            log.warn("Endpoint {} paused automatically after {} dead deliveries in a row", endpointId, threshold);
        }
    }

    private static String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String rawPayload(Delivery delivery) {
        try {
            return objectMapper.writeValueAsString(delivery.getEvent().getPayload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored event payload could not be serialized", exception);
        }
    }
}
