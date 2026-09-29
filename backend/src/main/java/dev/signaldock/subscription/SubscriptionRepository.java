package dev.signaldock.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubscriptionRepository extends JpaRepository<EndpointSubscription, UUID> {
    @EntityGraph(attributePaths = "endpoint")
    List<EndpointSubscription> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "endpoint")
    Optional<EndpointSubscription> findByIdAndActiveTrue(UUID id);

    boolean existsByEndpointIdAndEventPattern(UUID endpointId, String eventPattern);

    @Query("""
            select subscription
            from EndpointSubscription subscription
            join fetch subscription.endpoint endpoint
            where subscription.active = true
              and endpoint.active = true
            """)
    List<EndpointSubscription> findActiveWithActiveEndpoints();
}

