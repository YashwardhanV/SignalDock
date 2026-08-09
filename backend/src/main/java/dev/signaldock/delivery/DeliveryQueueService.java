package dev.signaldock.delivery;

import dev.signaldock.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryQueueService {
    private final AppProperties properties;
    private final Clock clock;
    private final DeliveryRepository deliveryRepository;

    public DeliveryQueueService(AppProperties properties, Clock clock, DeliveryRepository deliveryRepository) {
        this.properties = properties;
        this.clock = clock;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public List<UUID> claimDue(String workerId) {
        Instant now = clock.instant();
        List<Delivery> due = deliveryRepository.lockDueBatch(now, properties.delivery().batchSize());
        due.forEach(delivery -> delivery.claim(workerId, now, properties.delivery().leaseDuration()));
        return due.stream().map(Delivery::getId).toList();
    }
}

