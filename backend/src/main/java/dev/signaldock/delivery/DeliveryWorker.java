package dev.signaldock.delivery;

import java.lang.management.ManagementFactory;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final DeliveryHttpClient httpClient;
    private final DeliveryProcessingService processingService;
    private final DeliveryQueueService queueService;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();

    public DeliveryWorker(
            DeliveryHttpClient httpClient,
            DeliveryProcessingService processingService,
            DeliveryQueueService queueService
    ) {
        this.httpClient = httpClient;
        this.processingService = processingService;
        this.queueService = queueService;
    }

    @Scheduled(fixedDelayString = "${app.delivery.poll-delay}")
    public void poll() {
        queueService.claimDue(workerId).forEach(this::process);
    }

    private void process(UUID deliveryId) {
        try {
            processingService.prepare(deliveryId).ifPresent(item -> {
                DeliveryResult result = httpClient.deliver(item);
                processingService.recordOutcome(deliveryId, result);
            });
        } catch (RuntimeException exception) {
            log.error("Unexpected delivery worker failure for {}. The lease will make it recoverable.", deliveryId, exception);
        }
    }
}

