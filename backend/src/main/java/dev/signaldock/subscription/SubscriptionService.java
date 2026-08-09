package dev.signaldock.subscription;

import dev.signaldock.endpoint.EndpointRepository;
import dev.signaldock.exception.ConflictException;
import dev.signaldock.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {
    private final EndpointRepository endpointRepository;
    private final EventPatternMatcher matcher;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(
            EndpointRepository endpointRepository,
            EventPatternMatcher matcher,
            SubscriptionRepository subscriptionRepository
    ) {
        this.endpointRepository = endpointRepository;
        this.matcher = matcher;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public SubscriptionDtos.Response create(UUID endpointId, SubscriptionDtos.CreateRequest request) {
        var endpoint = endpointRepository.findByIdAndActiveTrue(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Active endpoint not found: " + endpointId));
        String pattern = request.eventPattern().trim();
        matcher.validate(pattern);
        if (subscriptionRepository.existsByEndpointIdAndEventPatternIgnoreCase(endpointId, pattern)) {
            throw new ConflictException("This endpoint already has that event pattern.");
        }
        return SubscriptionDtos.Response.from(subscriptionRepository.save(new EndpointSubscription(endpoint, pattern)));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDtos.Response> list() {
        return subscriptionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(SubscriptionDtos.Response::from)
                .toList();
    }

    @Transactional
    public void deactivate(UUID subscriptionId) {
        EndpointSubscription subscription = subscriptionRepository.findByIdAndActiveTrue(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Active subscription not found: " + subscriptionId));
        subscription.deactivate();
    }
}

