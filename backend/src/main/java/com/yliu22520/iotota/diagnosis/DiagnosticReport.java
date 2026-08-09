package com.yliu22520.iotota.diagnosis;

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
import java.util.UUID;

@Entity
@Table(name = "diagnostic_report")
public class DiagnosticReport {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagnostic_task_id", nullable = false, unique = true)
    private DiagnosticTask diagnosticTask;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report", columnDefinition = "jsonb", nullable = false)
    private DiagnosticReportDocument report;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DiagnosticReport() {
    }

    public DiagnosticReport(UUID id, DiagnosticTask diagnosticTask, DiagnosticReportDocument report,
                            Instant createdAt) {
        this.id = id;
        this.diagnosticTask = diagnosticTask;
        this.report = report;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public DiagnosticTask getDiagnosticTask() {
        return diagnosticTask;
    }

    public DiagnosticReportDocument getReport() {
        return report;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
