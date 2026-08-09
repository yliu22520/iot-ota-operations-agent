package com.yliu22520.iotota.diagnosis;

import java.time.Instant;
import java.util.UUID;

public final class DiagnosticApiModels {

    private DiagnosticApiModels() {
    }

    public record StartResponse(UUID diagnosticTaskId, UUID upgradeTaskId, String state, String statusUrl) {
    }

    public record TaskView(UUID diagnosticTaskId, UUID upgradeTaskId, String state, String status,
                           boolean terminal, Instant createdAt, Instant updatedAt,
                           DiagnosticReportDocument report) {
    }
}
