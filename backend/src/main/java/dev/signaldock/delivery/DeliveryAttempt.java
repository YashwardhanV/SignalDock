package dev.signaldock.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {
    static final int MAX_TEXT_LENGTH = 4000; // matches VARCHAR(4000) columns

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_body", length = 4000)
    private String responseBody;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    protected DeliveryAttempt() {
    }

    public DeliveryAttempt(Delivery delivery, int attemptNumber, DeliveryResult result) {
        this.id = UUID.randomUUID();
        this.delivery = delivery;
        this.attemptNumber = attemptNumber;
        this.startedAt = result.startedAt();
        this.finishedAt = result.finishedAt();
        this.httpStatus = result.httpStatus();
        this.responseBody = truncate(result.responseBody());
        this.errorMessage = truncate(result.errorMessage());
        this.latencyMs = result.latencyMs();
    }

    static String truncate(String value) {
        return value == null || value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }

    public UUID getId() {
        return id;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}

