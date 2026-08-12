package com.yliu22520.iotota.action;

import com.yliu22520.iotota.diagnosis.DiagnosticTask;
import com.yliu22520.iotota.diagnosis.RetryPlanDocument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retry_plan")
public class RetryPlan {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagnostic_task_id", nullable = false)
    private DiagnosticTask diagnosticTask;

    @Column(name = "plan_version", nullable = false)
    private int planVersion;

    @Column(nullable = false)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private RetryPlanDocument snapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected RetryPlan() {
    }

    public RetryPlan(UUID id, DiagnosticTask diagnosticTask, int planVersion, String status,
                     Instant expiresAt, RetryPlanDocument snapshot, Instant createdAt) {
        this.id = id;
        this.diagnosticTask = diagnosticTask;
        this.planVersion = planVersion;
        this.status = status;
        this.expiresAt = expiresAt;
        this.snapshot = snapshot;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public DiagnosticTask getDiagnosticTask() { return diagnosticTask; }
    public int getPlanVersion() { return planVersion; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public RetryPlanDocument getSnapshot() { return snapshot; }
    public Instant getCreatedAt() { return createdAt; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }

    public void approve(String actor, Instant approvedAt) {
        this.status = "APPROVED";
        this.approvedBy = actor;
        this.approvedAt = approvedAt;
    }

    public void reject(String rejectionStatus) {
        this.status = rejectionStatus;
    }

    public void markExecuted() {
        this.status = "EXECUTED";
    }
}
