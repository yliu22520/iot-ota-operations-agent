package com.yliu22520.iotota.diagnosis;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Evaluation artifact data; it intentionally has no prompt, raw response, or hidden reasoning field. */
public record DiagnosticEvaluationReport(
        String provider,
        String configurationId,
        String modelId,
        String reasoningTier,
        int reasoningBudgetTokens,
        String promptVersion,
        String toolSchemaVersion,
        DiagnosticExecutionLimits executionLimits,
        boolean automaticFallbackEnabled,
        List<DiagnosticEvaluationCase> cases,
        List<DiagnosticEvaluationObservation> observations,
        DiagnosticReleaseDecision releaseDecision) {

    public DiagnosticEvaluationReport(String provider,
                                      String configurationId,
                                      String modelId,
                                      String reasoningTier,
                                      String promptVersion,
                                      String toolSchemaVersion,
                                      List<DiagnosticEvaluationObservation> observations,
                                      DiagnosticReleaseDecision releaseDecision) {
        this(provider, configurationId, modelId, reasoningTier, 0, promptVersion, toolSchemaVersion,
                observations, releaseDecision);
    }

    public DiagnosticEvaluationReport(String provider,
                                      String configurationId,
                                      String modelId,
                                      String reasoningTier,
                                      int reasoningBudgetTokens,
                                      String promptVersion,
                                      String toolSchemaVersion,
                                      List<DiagnosticEvaluationObservation> observations,
                                      DiagnosticReleaseDecision releaseDecision) {
        this(provider, configurationId, modelId, reasoningTier, reasoningBudgetTokens, promptVersion, toolSchemaVersion,
                DiagnosticExecutionLimits.v1(), false, DiagnosticEvaluationCaseCatalog.all(), observations,
                releaseDecision);
    }

    public DiagnosticEvaluationReport(String configurationId,
                                      String modelId,
                                      String reasoningTier,
                                      String promptVersion,
                                      String toolSchemaVersion,
                                      List<DiagnosticEvaluationObservation> observations,
                                      DiagnosticReleaseDecision releaseDecision) {
        this(providerFromConfigurationId(configurationId), configurationId, modelId, reasoningTier, promptVersion,
                toolSchemaVersion, observations, releaseDecision);
    }

    public DiagnosticEvaluationReport(String configurationId,
                                      String modelId,
                                      String reasoningTier,
                                      int reasoningBudgetTokens,
                                      String promptVersion,
                                      String toolSchemaVersion,
                                      List<DiagnosticEvaluationObservation> observations,
                                      DiagnosticReleaseDecision releaseDecision) {
        this(providerFromConfigurationId(configurationId), configurationId, modelId, reasoningTier,
                reasoningBudgetTokens, promptVersion, toolSchemaVersion, observations, releaseDecision);
    }

    public DiagnosticEvaluationReport {
        if (reasoningBudgetTokens < 0) {
            throw new IllegalArgumentException("Evaluation reasoning budget cannot be negative");
        }
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

    @JsonProperty("promptId")
    public String promptId() {
        return idFromVersion(promptVersion);
    }

    @JsonProperty("toolSchemaId")
    public String toolSchemaId() {
        return idFromVersion(toolSchemaVersion);
    }

    private static String providerFromConfigurationId(String configurationId) {
        int separator = configurationId.indexOf(':');
        return separator < 0 ? "unknown" : configurationId.substring(0, separator);
    }

    private static String idFromVersion(String version) {
        int versionSeparator = version.lastIndexOf("-v");
        return versionSeparator > 0 ? version.substring(0, versionSeparator) : version;
    }
}
