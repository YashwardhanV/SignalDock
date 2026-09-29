package dev.signaldock.delivery;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final DeliveryHttpClient httpClient;
    private final DeliveryWorkerService workerService;

    public DeliveryWorker(DeliveryHttpClient httpClient, DeliveryWorkerService workerService) {
        this.httpClient = httpClient;
        this.workerService = workerService;
    }

    @Scheduled(fixedDelayString = "${app.delivery.poll-delay}")
    public void poll() {
        workerService.claimDue().forEach(this::process);
    }

    // Each workerService call goes through Spring's proxy from this separate bean, so its @Transactional applies.
    private void process(UUID deliveryId) {
        try {
            workerService.prepare(deliveryId).ifPresent(item -> {
                DeliveryResult result = httpClient.deliver(item);
                workerService.recordOutcome(deliveryId, result);
            });
        } catch (RuntimeException exception) {
            log.error("Unexpected delivery worker failure for {}. The lease will make it recoverable.", deliveryId, exception);
        }
    }
}
