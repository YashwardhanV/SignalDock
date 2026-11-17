package dev.signaldock.endpoint;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    Page<WebhookEndpoint> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<WebhookEndpoint> findByIdAndActiveTrue(UUID id);

    // Endpoint health is changed with single atomic UPDATE statements, never read-modify-write in Java,
    // because several delivery threads can record outcomes for the same endpoint at the same time.

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update webhook_endpoints
            set consecutive_dead_deliveries = 0
            where id = :id and consecutive_dead_deliveries <> 0
            """, nativeQuery = true)
    int resetDeadStreak(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update webhook_endpoints
            set consecutive_dead_deliveries = consecutive_dead_deliveries + 1
            where id = :id
            """, nativeQuery = true)
    int incrementDeadStreak(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update webhook_endpoints
            set active = false, disabled_reason = :reason, disabled_at = :now, updated_at = :now
            where id = :id and active and consecutive_dead_deliveries >= :threshold
            """, nativeQuery = true)
    int disableIfDeadStreakReached(
            @Param("id") UUID id,
            @Param("threshold") int threshold,
            @Param("reason") String reason,
            @Param("now") Instant now
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update webhook_endpoints
            set active = false, disabled_reason = :reason, disabled_at = :now, updated_at = :now
            where id = :id and active
            """, nativeQuery = true)
    int disable(@Param("id") UUID id, @Param("reason") String reason, @Param("now") Instant now);
}
