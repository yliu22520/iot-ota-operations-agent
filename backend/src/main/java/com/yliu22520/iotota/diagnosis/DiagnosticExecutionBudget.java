package com.yliu22520.iotota.diagnosis;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Mutable per-diagnosis budget; it contains counters but no domain decision logic. */
public final class DiagnosticExecutionBudget {

    private final DiagnosticExecutionLimits limits;
    private final Clock clock;
    private final Instant startedAt;
    private int toolCalls;
    private int modelInteractions;
    private int inputTokens;
    private int outputTokens;

    public DiagnosticExecutionBudget(DiagnosticExecutionLimits limits, Clock clock) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startedAt = clock.instant();
    }

    public void beforeToolCall() {
        checkTime();
        if (toolCalls >= limits.maxToolCalls()) {
            throw exceeded("TOOL_CALL_LIMIT", "Diagnostic tool-call budget exhausted");
        }
        toolCalls++;
    }

    public void beforeModelInteraction() {
        checkTime();
        if (modelInteractions >= limits.maxModelInteractions()) {
            throw exceeded("MODEL_INTERACTION_LIMIT", "Diagnostic model-interaction budget exhausted");
        }
        modelInteractions++;
    }

    public void recordModelUsage(int inputTokens, int outputTokens) {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("Model token usage cannot be negative");
        }
        if (outputTokens > limits.maxOutputTokens()) {
            throw exceeded("MODEL_OUTPUT_LIMIT", "Diagnostic model output exceeded the configured limit");
        }
        if (inputTokens > limits.maxContextTokens()) {
            throw exceeded("MODEL_CONTEXT_LIMIT", "Diagnostic model context exceeded the configured limit");
        }
        this.inputTokens = Math.addExact(this.inputTokens, inputTokens);
        this.outputTokens = Math.addExact(this.outputTokens, outputTokens);
        checkTime();
    }

    public int toolCalls() {
        return toolCalls;
    }

    public int modelInteractions() {
        return modelInteractions;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public DiagnosticExecutionLimits limits() {
        return limits;
    }

    public void ensureWithinLimit() {
        checkTime();
    }

    private void checkTime() {
        Instant deadline = startedAt.plus(limits.maxDuration());
        if (!clock.instant().isBefore(deadline)) {
            throw exceeded("TOTAL_TIME_LIMIT", "Diagnostic execution time budget exhausted");
        }
    }

    private DiagnosticBudgetExceededException exceeded(String reasonCode, String message) {
        return new DiagnosticBudgetExceededException(reasonCode, message);
    }
}
