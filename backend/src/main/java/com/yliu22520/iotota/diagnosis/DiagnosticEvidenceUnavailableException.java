package com.yliu22520.iotota.diagnosis;

public class DiagnosticEvidenceUnavailableException extends RuntimeException {

    private final StructuredToolResult<?> result;

    public DiagnosticEvidenceUnavailableException(StructuredToolResult<?> result) {
        super(result.error());
        this.result = result;
    }

    public StructuredToolResult<?> result() {
        return result;
    }
}
