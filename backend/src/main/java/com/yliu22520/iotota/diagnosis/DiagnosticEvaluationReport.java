package com.yliu22520.iotota.diagnosis;

import java.util.List;

/** Evaluation artifact data; it intentionally has no prompt, raw response, or reasoning field. */
public record DiagnosticEvaluationReport(
        String configurationId,
        String modelId,
        String reasoningTier,
        String promptVersion,
        String toolSchemaVersion,
        DiagnosticExecutionLimits executionLimits,
        boolean automaticFallbackEnabled,
        List<DiagnosticEvaluationCase> cases,
        List<DiagnosticEvaluationObservation> observations,
        DiagnosticReleaseDecision releaseDecision) {

    public DiagnosticEvaluationReport(String configurationId,
                                      String modelId,
                                      String reasoningTier,
                                      String promptVersion,
                                      String toolSchemaVersion,
                                      List<DiagnosticEvaluationObservation> observations,
                                      DiagnosticReleaseDecision releaseDecision) {
        this(configurationId, modelId, reasoningTier, promptVersion, toolSchemaVersion,
                DiagnosticExecutionLimits.v1(), false, DiagnosticEvaluationCaseCatalog.all(), observations,
                releaseDecision);
    }

    public DiagnosticEvaluationReport {
        if (executionLimits == null) {
            throw new IllegalArgumentException("Evaluation execution limits are required");
        }
        cases = List.copyOf(cases);
        observations = List.copyOf(observations);
    }

    public double passRate() {
        if (observations.isEmpty()) {
            return 0.0d;
        }
        return observations.stream().filter(DiagnosticEvaluationObservation::passed).count()
                / (double) observations.size();
    }
}
