package dev.signaldock.delivery;

import dev.signaldock.config.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class DeliveryController {
    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/deliveries")
    PageResponse<DeliveryDtos.Response> list(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        return deliveryService.list(status, page, size);
    }

    @GetMapping("/deliveries/{deliveryId}")
    DeliveryDtos.DetailResponse detail(@PathVariable UUID deliveryId) {
        return deliveryService.detail(deliveryId);
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    DeliveryDtos.Response retry(@PathVariable UUID deliveryId) {
        return deliveryService.retry(deliveryId);
    }

    @GetMapping("/dashboard/summary")
    DeliveryDtos.DashboardSummary summary() {
        return deliveryService.summary();
    }
}

