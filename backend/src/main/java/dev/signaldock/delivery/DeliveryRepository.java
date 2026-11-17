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
    /*
     * Claims up to :batchSize due deliveries, but never lets any one endpoint have more than
     * :perEndpointLimit deliveries in flight (claimed with a live lease), counting work already
     * in flight on any worker. One endpoint with a huge backlog therefore cannot fill the batch
     * and starve the others. Paused endpoints are skipped entirely.
     *
     * Postgres does not allow FOR UPDATE next to window functions, so the ranking happens in a
     * subquery and the outer query locks the chosen rows and re-checks that they are still due.
     * If two workers rank at the same moment, the rows one of them locks are skipped by the
     * other, which then claims nothing for that endpoint instead of exceeding the limit.
     */
    @Query(value = """
            select d.*
            from deliveries d
            where d.id in (
                select ranked.id
                from (
                    select candidate.id,
                           candidate.next_retry_at,
                           candidate.created_at,
                           (candidate.status = 'PROCESSING' and candidate.lease_until >= :now) as in_flight,
                           row_number() over (
                               partition by candidate.endpoint_id
                               order by (candidate.status = 'PROCESSING' and candidate.lease_until >= :now) desc,
                                        candidate.next_retry_at,
                                        candidate.created_at
                           ) as endpoint_rank
                    from deliveries candidate
                    join webhook_endpoints endpoint on endpoint.id = candidate.endpoint_id
                    where endpoint.active
                      and (
                          (candidate.status in ('PENDING', 'RETRY_PENDING') and candidate.next_retry_at <= :now)
                          or candidate.status = 'PROCESSING'
                      )
                ) ranked
                where ranked.endpoint_rank <= :perEndpointLimit
                  and not ranked.in_flight
                order by ranked.next_retry_at, ranked.created_at
                limit :batchSize
            )
            and (
                (d.status in ('PENDING', 'RETRY_PENDING') and d.next_retry_at <= :now)
                or (d.status = 'PROCESSING' and d.lease_until < :now)
            )
            for update of d skip locked
            """, nativeQuery = true)
    List<Delivery> lockDueBatch(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize,
            @Param("perEndpointLimit") int perEndpointLimit
    );

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

    long countByStatus(DeliveryStatus status);
}

