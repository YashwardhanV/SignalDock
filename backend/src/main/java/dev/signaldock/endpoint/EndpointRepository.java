package dev.signaldock.endpoint;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    Page<WebhookEndpoint> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<WebhookEndpoint> findByIdAndActiveTrue(UUID id);

    Optional<WebhookEndpoint> findByUrlIgnoreCaseAndActiveTrue(String url);

    boolean existsByUrlIgnoreCaseAndActiveTrue(String url);

    boolean existsByUrlIgnoreCaseAndActiveTrueAndIdNot(String url, UUID id);
}
