package com.yliu22520.iotota.diagnosis;

/** Signals that a diagnosis must stop without attempting another model or tool call. */
public class DiagnosticBudgetExceededException extends RuntimeException {

    private final String reasonCode;

    public DiagnosticBudgetExceededException(String reasonCode, String message) {
        super(reasonCode + ": " + message);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
