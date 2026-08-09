package com.yliu22520.iotota.diagnosis;

import java.util.UUID;

public class DiagnosticAlreadyActiveException extends RuntimeException {

    private final UUID activeDiagnosticTaskId;

    public DiagnosticAlreadyActiveException(UUID activeDiagnosticTaskId) {
        super("An active diagnosis already exists for this upgrade task");
        this.activeDiagnosticTaskId = activeDiagnosticTaskId;
    }

    public UUID getActiveDiagnosticTaskId() {
        return activeDiagnosticTaskId;
    }
}
