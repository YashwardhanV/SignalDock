package dev.signaldock.demo;

import dev.signaldock.config.AppProperties;
import dev.signaldock.endpoint.EndpointRepository;
import dev.signaldock.endpoint.WebhookEndpoint;
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
    private final AppProperties properties;
    private final EndpointRepository endpointRepository;
    private final SubscriptionRepository subscriptionRepository;

    public DemoDataInitializer(
            AppProperties properties,
            EndpointRepository endpointRepository,
            SubscriptionRepository subscriptionRepository
    ) {
        this.properties = properties;
        this.endpointRepository = endpointRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (endpointRepository.count() > 0) {
            return;
        }

        WebhookEndpoint success = endpointRepository.save(new WebhookEndpoint(
                "Demo receiver",
                properties.demo().receiverUrl(),
                "demo-success-signing-secret",
                5
        ));
        subscriptionRepository.save(new EndpointSubscription(success, "order.created"));
        subscriptionRepository.save(new EndpointSubscription(success, "benchmark.delivery"));

        WebhookEndpoint failure = endpointRepository.save(new WebhookEndpoint(
                "Retry lab",
                properties.demo().failureUrl(),
                "demo-failure-signing-secret",
                3
        ));
        subscriptionRepository.save(new EndpointSubscription(failure, "benchmark.retry"));
    }
}
