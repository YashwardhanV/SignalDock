package dev.signaldock.delivery;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /**
     * Claims a batch and sends every delivery in it at the same time, one virtual thread each.
     * A slow or dead receiver now only delays its own deliveries, not the whole batch. The claim
     * query already capped how many deliveries each endpoint got, so a single endpoint can never
     * take all the threads. The poll waits for the batch to finish (each send is bounded by
     * connect-timeout + read-timeout), so polls never overlap.
     */
    @Scheduled(fixedDelayString = "${app.delivery.poll-delay}")
    public void poll() {
        List<ClaimedDelivery> claimed = workerService.claimDue();
        if (claimed.isEmpty()) {
            return;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            claimed.forEach(claim -> executor.execute(() -> process(claim)));
        }
    }

    // Each workerService call goes through Spring's proxy from this separate bean, so its @Transactional applies.
    private void process(ClaimedDelivery claim) {
        try {
            workerService.prepare(claim).ifPresent(item -> {
                DeliveryResult result = httpClient.deliver(item);
                workerService.recordOutcome(claim, result);
            });
        } catch (RuntimeException exception) {
            log.error("Unexpected delivery worker failure for {}. The lease will make it recoverable.", claim.deliveryId(), exception);
        }
    }
}
