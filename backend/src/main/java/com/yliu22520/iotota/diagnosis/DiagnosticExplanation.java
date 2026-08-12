package com.yliu22520.iotota.diagnosis;

/** Model explanation text plus non-sensitive usage telemetry. */
public record DiagnosticExplanation(String text, int inputTokens, int outputTokens) {

    public DiagnosticExplanation(String text) {
        this(text, 0, 0);
    }
}
