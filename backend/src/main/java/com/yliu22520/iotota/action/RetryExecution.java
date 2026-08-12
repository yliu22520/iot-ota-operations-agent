package com.yliu22520.iotota.action;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "retry_execution")
public class RetryExecution {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retry_plan_id", nullable = false, unique = true)
    private RetryPlan retryPlan;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RetryExecution() {
    }

    public RetryExecution(UUID id, RetryPlan retryPlan, String idempotencyKey, Instant createdAt) {
        this.id = id;
        this.retryPlan = retryPlan;
        this.idempotencyKey = idempotencyKey;
        this.status = "INTENT_RECORDED";
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public RetryPlan getRetryPlan() { return retryPlan; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public Map<String, Object> getResult() { return result; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void recordCallAccepted(UUID attemptId, Instant acceptedAt, Instant changedAt) {
        status = "CALL_ACCEPTED";
        result = Map.of("simulatorAttemptId", attemptId.toString(), "acceptedAt", acceptedAt.toString());
        updatedAt = changedAt;
    }

    public void markVerified(Instant changedAt) {
        status = "VERIFIED";
        result = new java.util.HashMap<>(result == null ? Map.of() : result);
        result.put("verificationStatus", "BUSINESS_ACCEPTED");
        updatedAt = changedAt;
    }

    public void markIncomplete(String reason, Instant changedAt) {
        status = "INCOMPLETE";
        result = Map.of("verificationStatus", "INCOMPLETE", "reason", reason);
        updatedAt = changedAt;
    }
}
