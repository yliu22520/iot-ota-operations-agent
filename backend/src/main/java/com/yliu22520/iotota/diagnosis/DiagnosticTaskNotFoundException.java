package com.yliu22520.iotota.diagnosis;

import java.util.UUID;

public class DiagnosticTaskNotFoundException extends RuntimeException {

    public DiagnosticTaskNotFoundException(UUID id) {
        super("Diagnostic task not found: " + id);
    }
}
