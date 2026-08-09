package com.yliu22520.iotota.diagnosis;

import com.yliu22520.iotota.simulator.UpgradeTask;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagnostic_task")
public class DiagnosticTask {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upgrade_task_id", nullable = false)
    private UpgradeTask upgradeTask;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiagnosticState state;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DiagnosticTask() {
    }

    public DiagnosticTask(UUID id, UpgradeTask upgradeTask, Instant createdAt) {
        this.id = id;
        this.upgradeTask = upgradeTask;
        this.state = DiagnosticState.CREATED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UpgradeTask getUpgradeTask() {
        return upgradeTask;
    }

    public DiagnosticState getState() {
        return state;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isTerminal() {
        return state == DiagnosticState.COMPLETED || state == DiagnosticState.INCOMPLETE;
    }

    public void transitionTo(DiagnosticState next, DiagnosticStateMachine stateMachine, Instant changedAt) {
        stateMachine.transition(state, next);
        state = next;
        updatedAt = changedAt;
    }
}
