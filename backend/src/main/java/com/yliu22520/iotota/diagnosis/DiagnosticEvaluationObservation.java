package com.yliu22520.iotota.diagnosis;

import java.util.List;
import java.util.Objects;

/** One case execution's structured outcome and safe telemetry, with no raw prompt or chain of thought. */
public record DiagnosticEvaluationObservation(
        String caseId,
        DiagnosticEvaluationCase.Type type,
        int runNumber,
        boolean passed,
        List<SafetyViolation> safetyViolations,
        String failureClass,
        int toolCalls,
        int modelInteractions,
        int inputTokens,
        int outputTokens,
        long durationMs,
        String actualOutcome) {

    public DiagnosticEvaluationObservation(String caseId,
                                           DiagnosticEvaluationCase.Type type,
                                           int runNumber,
                                           boolean passed,
                                           List<SafetyViolation> safetyViolations,
                                           String failureClass,
                                           int toolCalls,
                                           int modelInteractions,
                                           int inputTokens,
                                           int outputTokens,
                                           long durationMs) {
        this(caseId, type, runNumber, passed, safetyViolations, failureClass, toolCalls, modelInteractions,
                inputTokens, outputTokens, durationMs, defaultOutcome(passed, failureClass));
    }

    public DiagnosticEvaluationObservation {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(type, "type");
        safetyViolations = List.copyOf(safetyViolations);
        actualOutcome = actualOutcome == null || actualOutcome.isBlank()
                ? defaultOutcome(passed, failureClass)
                : actualOutcome;
        if (runNumber < 1 || runNumber > 3 || toolCalls < 0 || modelInteractions < 0 || inputTokens < 0
                || outputTokens < 0 || durationMs < 0) {
            throw new IllegalArgumentException("Invalid evaluation observation");
        }
    }

    private static String defaultOutcome(boolean passed, String failureClass) {
        return passed ? "PASS" : failureClass == null ? "UNKNOWN_FAILURE" : failureClass;
    }

    public enum SafetyViolation {
        UNAUTHORIZED_WRITE,
        APPROVAL_BYPASS,
        PRIVILEGE_ESCALATION,
        FORGED_EXECUTION_RESULT,
        UNSAFE_RETRY
    }
}
