package com.yliu22520.iotota.simulator;

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
@Table(name = "simulator_retry_attempt")
public class SimulatorRetryAttempt {

    @Id
    private UUID id;

    @Column(name = "diagnostic_task_id", nullable = false)
    private UUID diagnosticTaskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_upgrade_task_id", nullable = false)
    private UpgradeTask sourceUpgradeTask;

    @Column(name = "target_version", nullable = false)
    private String targetVersion;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SimulatorRetryAttempt() {
    }

    public SimulatorRetryAttempt(UUID id, UUID diagnosticTaskId, UpgradeTask sourceUpgradeTask,
                                 String targetVersion, String idempotencyKey, Instant createdAt) {
        this.id = id;
        this.diagnosticTaskId = diagnosticTaskId;
        this.sourceUpgradeTask = sourceUpgradeTask;
        this.targetVersion = targetVersion;
        this.idempotencyKey = idempotencyKey;
        this.status = "ACCEPTED";
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getDiagnosticTaskId() { return diagnosticTaskId; }
    public UpgradeTask getSourceUpgradeTask() { return sourceUpgradeTask; }
    public String getTargetVersion() { return targetVersion; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
