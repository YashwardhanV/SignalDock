package dev.signaldock.subscription;

import dev.signaldock.endpoint.WebhookEndpoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "endpoint_subscriptions")
public class EndpointSubscription {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private WebhookEndpoint endpoint;

    @Column(name = "event_pattern", nullable = false, length = 160)
    private String eventPattern;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EndpointSubscription() {
    }

    public EndpointSubscription(WebhookEndpoint endpoint, String eventPattern) {
        this.id = UUID.randomUUID();
        this.endpoint = endpoint;
        this.eventPattern = eventPattern;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public WebhookEndpoint getEndpoint() {
        return endpoint;
    }

    public String getEventPattern() {
        return eventPattern;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

