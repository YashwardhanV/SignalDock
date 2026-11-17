package dev.signaldock.endpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

// DynamicUpdate: only changed columns are written, so editing an endpoint never overwrites the
// health counter that delivery threads update with their own atomic SQL.
@DynamicUpdate
@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "signing_secret", nullable = false, length = 180)
    private String signingSecret;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "consecutive_dead_deliveries", nullable = false)
    private int consecutiveDeadDeliveries;

    @Column(name = "disabled_reason", length = 500)
    private String disabledReason;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookEndpoint() {
    }

    public WebhookEndpoint(String name, String url, String signingSecret, int maxAttempts) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.url = url;
        this.signingSecret = signingSecret;
        this.maxAttempts = maxAttempts;
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

    public void update(String name, String url, int maxAttempts, boolean active) {
        this.name = name;
        this.url = url;
        this.maxAttempts = maxAttempts;
        if (active && !this.active) {
            // A person turning the endpoint back on starts it with a clean health record.
            this.consecutiveDeadDeliveries = 0;
            this.disabledReason = null;
            this.disabledAt = null;
        }
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public boolean isActive() {
        return active;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getConsecutiveDeadDeliveries() {
        return consecutiveDeadDeliveries;
    }

    public String getDisabledReason() {
        return disabledReason;
    }

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

