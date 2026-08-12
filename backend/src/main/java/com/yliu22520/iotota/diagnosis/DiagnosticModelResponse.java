package com.yliu22520.iotota.diagnosis;

/** Sanitized model response; raw provider messages and reasoning fields never cross this seam. */
public record DiagnosticModelResponse(String text, int inputTokens, int outputTokens) {

    public DiagnosticModelResponse {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Model response text must not be blank");
        }
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("Model token usage cannot be negative");
        }
    }
}
