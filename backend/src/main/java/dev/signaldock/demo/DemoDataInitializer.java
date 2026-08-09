package dev.signaldock.demo;

import dev.signaldock.config.AppProperties;
import dev.signaldock.endpoint.EndpointRepository;
import dev.signaldock.endpoint.WebhookEndpoint;
import dev.signaldock.security.ApiKey;
import dev.signaldock.security.ApiKeyHasher;
import dev.signaldock.security.ApiKeyRepository;
import dev.signaldock.subscription.EndpointSubscription;
import dev.signaldock.subscription.SubscriptionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyRepository apiKeyRepository;
    private final AppProperties properties;
    private final EndpointRepository endpointRepository;
    private final SubscriptionRepository subscriptionRepository;

    public DemoDataInitializer(
            ApiKeyHasher apiKeyHasher,
            ApiKeyRepository apiKeyRepository,
            AppProperties properties,
            EndpointRepository endpointRepository,
            SubscriptionRepository subscriptionRepository
    ) {
        this.apiKeyHasher = apiKeyHasher;
        this.apiKeyRepository = apiKeyRepository;
        this.properties = properties;
        this.endpointRepository = endpointRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String keyHash = apiKeyHasher.hash(properties.demo().apiKey());
        if (!apiKeyRepository.existsByKeyHash(keyHash)) {
            apiKeyRepository.save(new ApiKey("Local demo key", keyHash));
        }

        WebhookEndpoint success = endpoint(
                "Demo receiver",
                properties.demo().receiverUrl(),
                "demo-success-signing-secret",
                5
        );
        subscribe(success, "order.created");
        subscribe(success, "benchmark.delivery");

        WebhookEndpoint failure = endpoint(
                "Retry lab",
                properties.demo().failureUrl(),
                "demo-failure-signing-secret",
                3
        );
        subscribe(failure, "benchmark.retry");
    }

    private WebhookEndpoint endpoint(String name, String url, String secret, int attempts) {
        return endpointRepository.findByUrlIgnoreCaseAndActiveTrue(url)
                .orElseGet(() -> endpointRepository.save(new WebhookEndpoint(name, url, secret, attempts)));
    }

    private void subscribe(WebhookEndpoint endpoint, String pattern) {
        if (!subscriptionRepository.existsByEndpointIdAndEventPatternIgnoreCase(endpoint.getId(), pattern)) {
            subscriptionRepository.save(new EndpointSubscription(endpoint, pattern));
        }
    }
}

