package com.yliu22520.iotota.diagnosis;

import java.time.Duration;

/** Fixed execution limits shared by deterministic runs and real-model evaluation. */
public record DiagnosticExecutionLimits(
        int maxToolCalls,
        int maxModelInteractions,
        Duration maxDuration,
        int maxOutputTokens,
        int maxContextTokens,
        int maxReadToolRetries) {

    public DiagnosticExecutionLimits {
        if (maxToolCalls <= 0 || maxModelInteractions <= 0 || maxOutputTokens <= 0
                || maxContextTokens <= 0 || maxReadToolRetries < 0) {
            throw new IllegalArgumentException("Diagnostic execution limits must be positive");
        }
        if (maxDuration == null || maxDuration.isZero() || maxDuration.isNegative()) {
            throw new IllegalArgumentException("Diagnostic execution duration must be positive");
        }
    }

    public static DiagnosticExecutionLimits v1() {
        return new DiagnosticExecutionLimits(10, 8, Duration.ofSeconds(90), 4_096, 32_768, 1);
    }
}
