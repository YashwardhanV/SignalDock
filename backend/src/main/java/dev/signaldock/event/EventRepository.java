package dev.signaldock.event;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findByIdempotencyKey(String idempotencyKey);

    Page<Event> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * True if the stored event for this key has the same type and payload. The comparison is done by
     * Postgres on jsonb, so key order, whitespace and number formatting ({"a":1.0} vs {"a":1}) do not matter.
     */
    @Query(value = """
            select count(*) > 0
            from events
            where idempotency_key = :idempotencyKey
              and event_type = :eventType
              and payload = cast(:payload as jsonb)
            """, nativeQuery = true)
    boolean storedRequestMatches(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("eventType") String eventType,
            @Param("payload") String payload
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into events (id, event_type, payload, idempotency_key, created_at)
            values (:id, :eventType, cast(:payload as jsonb), :idempotencyKey, :createdAt)
            on conflict (idempotency_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("eventType") String eventType,
            @Param("payload") String payload,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("createdAt") Instant createdAt
    );
}

