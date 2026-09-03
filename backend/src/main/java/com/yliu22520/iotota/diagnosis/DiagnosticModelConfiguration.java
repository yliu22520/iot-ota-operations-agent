package com.yliu22520.iotota.diagnosis;

import java.util.List;
import java.util.Objects;

/** Immutable release configuration exposed as structured telemetry, never as a user-controlled option. */
public record DiagnosticModelConfiguration(String provider,
                                           String modelId,
                                           String reasoningTier,
                                           int reasoningBudgetTokens,
                                           String promptVersion,
                                           String toolSchemaVersion,
                                           double temperature,
                                           double topP,
                             DiagnosticExecutionLimits limits,
                             boolean automaticFallbackEnabled) {

    public static final String GEMINI_PROVIDER = "gemini";
    public static final String RELEASE_MODEL_ID = "gemini-3.1-flash-lite";
    public static final int HIGH_REASONING_BUDGET_TOKENS = 4_096;

    public DiagnosticModelConfiguration {
        if (provider == null || provider.isBlank() || modelId == null || modelId.isBlank()
                || reasoningTier == null || reasoningTier.isBlank() || promptVersion == null
                || promptVersion.isBlank() || toolSchemaVersion == null || toolSchemaVersion.isBlank()) {
            throw new IllegalArgumentException("Diagnostic model configuration fields must not be blank");
        }
        if (reasoningBudgetTokens < 0) {
            throw new IllegalArgumentException("Diagnostic reasoning budget cannot be negative");
        }
        if (temperature < 0.0d || topP <= 0.0d || topP > 1.0d) {
            throw new IllegalArgumentException("Diagnostic model sampling parameters are invalid");
        }
        Objects.requireNonNull(limits, "limits");
    }

    public static DiagnosticModelConfiguration releaseBaseline() {
        return new DiagnosticModelConfiguration(
                GEMINI_PROVIDER,
                RELEASE_MODEL_ID,
                "HIGH",
                HIGH_REASONING_BUDGET_TOKENS,
                "diagnosis-agent-v2",
                "diagnostic-tools-v1",
                0.0d,
                1.0d,
                DiagnosticExecutionLimits.v1(),
                false);
    }

    public static DiagnosticModelConfiguration runtimeRelease() {
        return releaseBaseline();
    }

    public static DiagnosticModelConfiguration runtimeRelease(String modelId) {
        String requestedModelId = modelId == null || modelId.isBlank() ? RELEASE_MODEL_ID : modelId.trim();
        if (!RELEASE_MODEL_ID.equals(requestedModelId)) {
            throw new IllegalArgumentException("Runtime model must match pinned baseline: " + RELEASE_MODEL_ID);
        }
        return runtimeRelease();
    }

    public static DiagnosticModelConfiguration controlled() {
        return new DiagnosticModelConfiguration(
                "controlled",
                "controlled-diagnostic-explainer-v1",
                "OFF",
                0,
                "diagnosis-controlled-v1",
                "diagnostic-tools-v1",
                0.0d,
                1.0d,
                DiagnosticExecutionLimits.v1(),
                false);
    }

    public static DiagnosticModelConfiguration evaluationCandidate(String modelId) {
        if (!candidateModelIds().contains(modelId)) {
            throw new IllegalArgumentException("Unsupported evaluation candidate: " + modelId);
        }
        DiagnosticModelConfiguration baseline = releaseBaseline();
        return new DiagnosticModelConfiguration(
                baseline.provider(),
                modelId,
                baseline.reasoningTier(),
                baseline.reasoningBudgetTokens(),
                baseline.promptVersion(),
                baseline.toolSchemaVersion(),
                baseline.temperature(),
                baseline.topP(),
                baseline.limits(),
                baseline.automaticFallbackEnabled());
    }

    public static List<String> candidateModelIds() {
        return List.of(RELEASE_MODEL_ID);
    }

    public String configurationId() {
        return provider + ":" + modelId + ":" + reasoningTier + ":" + promptVersion + ":"
                + toolSchemaVersion + ":reasoningBudgetTokens=" + reasoningBudgetTokens
                + ":temperature=" + temperature + ":topP=" + topP;
    }

    public boolean supportsMultimodalInput() {
        return false;
    }
}
