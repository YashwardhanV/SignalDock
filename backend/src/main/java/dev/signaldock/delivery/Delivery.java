package dev.signaldock.delivery;

import dev.signaldock.endpoint.WebhookEndpoint;
import dev.signaldock.event.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deliveries")
public class Delivery {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private WebhookEndpoint endpoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "claimed_by", length = 120)
    private String claimedBy;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Delivery() {
    }

    public Delivery(Event event, WebhookEndpoint endpoint, Instant now) {
        this.id = UUID.randomUUID();
        this.event = event;
        this.endpoint = endpoint;
        this.status = DeliveryStatus.PENDING;
        this.maxAttempts = endpoint.getMaxAttempts();
        this.nextRetryAt = now;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void claim(String workerId, Instant now, Duration leaseDuration) {
        status = DeliveryStatus.PROCESSING;
        claimedBy = workerId;
        leaseUntil = now.plus(leaseDuration);
    }

    public void markDelivered(Instant completedAt) {
        attemptCount++;
        status = DeliveryStatus.DELIVERED;
        this.completedAt = completedAt;
        lastError = null;
        clearLease();
    }

    public void markFailedAttempt(String error, Instant nextAttemptAt, boolean exhausted) {
        attemptCount++;
        lastError = truncate(error);
        clearLease();
        if (exhausted) {
            status = DeliveryStatus.DEAD;
            completedAt = nextAttemptAt;
            nextRetryAt = nextAttemptAt;
        } else {
            status = DeliveryStatus.RETRY_PENDING;
            nextRetryAt = nextAttemptAt;
            completedAt = null;
        }
    }

    public void requeue(int additionalAttempts, Instant now) {
        maxAttempts += additionalAttempts;
        status = DeliveryStatus.RETRY_PENDING;
        nextRetryAt = now;
        completedAt = null;
        lastError = null;
        clearLease();
    }

    private void clearLease() {
        leaseUntil = null;
        claimedBy = null;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 4000) {
            return value;
        }
        return value.substring(0, 4000);
    }

    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public WebhookEndpoint getEndpoint() {
        return endpoint;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

