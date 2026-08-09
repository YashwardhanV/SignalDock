package dev.signaldock.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    @Query(value = """
            select *
            from deliveries
            where (
                status in ('PENDING', 'RETRY_PENDING')
                and next_retry_at <= :now
            ) or (
                status = 'PROCESSING'
                and lease_until < :now
            )
            order by next_retry_at asc, created_at asc
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<Delivery> lockDueBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"event", "endpoint"})
    @Query("select delivery from Delivery delivery where delivery.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"event", "endpoint"})
    @Query("select delivery from Delivery delivery where delivery.id = :id")
    Optional<Delivery> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"event", "endpoint"})
    Page<Delivery> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"event", "endpoint"})
    Page<Delivery> findAllByStatusOrderByCreatedAtDesc(DeliveryStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"event", "endpoint"})
    Page<Delivery> findAllByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant createdAfter, Pageable pageable);

    @EntityGraph(attributePaths = {"event", "endpoint"})
    Page<Delivery> findAllByStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            DeliveryStatus status,
            Instant createdAfter,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"event", "endpoint"})
    List<Delivery> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    long countByStatus(DeliveryStatus status);
}

