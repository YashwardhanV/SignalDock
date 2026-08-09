package dev.signaldock.delivery;

import dev.signaldock.config.PageResponse;
import dev.signaldock.exception.ConflictException;
import dev.signaldock.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryQueryService {
    private final Clock clock;
    private final DeliveryAttemptRepository attemptRepository;
    private final DeliveryRepository deliveryRepository;

    public DeliveryQueryService(
            Clock clock,
            DeliveryAttemptRepository attemptRepository,
            DeliveryRepository deliveryRepository
    ) {
        this.clock = clock;
        this.attemptRepository = attemptRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryDtos.Response> list(
            DeliveryStatus status,
            Instant createdAfter,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Delivery> deliveries;
        if (status != null && createdAfter != null) {
            deliveries = deliveryRepository.findAllByStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(status, createdAfter, pageable);
        } else if (status != null) {
            deliveries = deliveryRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (createdAfter != null) {
            deliveries = deliveryRepository.findAllByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(createdAfter, pageable);
        } else {
            deliveries = deliveryRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return PageResponse.from(deliveries, DeliveryDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.DetailResponse detail(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findDetailedById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + deliveryId));
        var attempts = attemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(deliveryId).stream()
                .map(DeliveryDtos.AttemptResponse::from)
                .toList();
        return new DeliveryDtos.DetailResponse(DeliveryDtos.Response.from(delivery), attempts);
    }

    @Transactional
    public DeliveryDtos.Response retry(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + deliveryId));
        if (delivery.getStatus() != DeliveryStatus.DEAD) {
            throw new ConflictException("Only DEAD deliveries can be retried manually.");
        }
        if (!delivery.getEndpoint().isActive()) {
            throw new ConflictException("Activate the endpoint before retrying this delivery.");
        }
        delivery.requeue(delivery.getEndpoint().getMaxAttempts(), clock.instant());
        return DeliveryDtos.Response.from(delivery);
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.DashboardSummary summary() {
        EnumMap<DeliveryStatus, Long> counts = new EnumMap<>(DeliveryStatus.class);
        long total = 0;
        for (DeliveryStatus status : DeliveryStatus.values()) {
            long count = deliveryRepository.countByStatus(status);
            counts.put(status, count);
            total += count;
        }
        long delivered = counts.get(DeliveryStatus.DELIVERED);
        long terminal = delivered + counts.get(DeliveryStatus.DEAD);
        double successRate = terminal == 0 ? 0 : Math.round(delivered * 10_000.0 / terminal) / 100.0;
        return new DeliveryDtos.DashboardSummary(total, Map.copyOf(counts), successRate);
    }
}

