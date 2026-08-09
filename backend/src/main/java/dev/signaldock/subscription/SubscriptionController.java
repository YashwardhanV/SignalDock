package dev.signaldock.subscription;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/endpoints/{endpointId}/subscriptions")
    ResponseEntity<SubscriptionDtos.Response> create(
            @PathVariable UUID endpointId,
            @Valid @RequestBody SubscriptionDtos.CreateRequest request
    ) {
        SubscriptionDtos.Response response = subscriptionService.create(endpointId, request);
        return ResponseEntity.created(URI.create("/api/v1/subscriptions/" + response.id())).body(response);
    }

    @GetMapping("/subscriptions")
    List<SubscriptionDtos.Response> list() {
        return subscriptionService.list();
    }

    @DeleteMapping("/subscriptions/{subscriptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable UUID subscriptionId) {
        subscriptionService.deactivate(subscriptionId);
    }
}

