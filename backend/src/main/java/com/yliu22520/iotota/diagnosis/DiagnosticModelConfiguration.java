package com.yliu22520.iotota.diagnosis;

import java.util.List;
import java.util.Objects;

/** Immutable release configuration exposed as structured telemetry, never as a user-controlled option. */
public record DiagnosticModelConfiguration(String provider,
                                           String modelId,
                                           String reasoningTier,
                                           String promptVersion,
                                           String toolSchemaVersion,
                                           double temperature,
                                           double topP,
                                           DiagnosticExecutionLimits limits,
                                           boolean automaticFallbackEnabled) {

    public DiagnosticModelConfiguration {
        if (provider == null || provider.isBlank() || modelId == null || modelId.isBlank()
                || reasoningTier == null || reasoningTier.isBlank() || promptVersion == null
                || promptVersion.isBlank() || toolSchemaVersion == null || toolSchemaVersion.isBlank()) {
            throw new IllegalArgumentException("Diagnostic model configuration fields must not be blank");
        }
        if (temperature < 0.0d || topP <= 0.0d || topP > 1.0d) {
            throw new IllegalArgumentException("Diagnostic model sampling parameters are invalid");
        }
        Objects.requireNonNull(limits, "limits");
    }

    public static DiagnosticModelConfiguration releaseBaseline() {
        return new DiagnosticModelConfiguration(
                "deepseek",
                "deepseek-v4-flash",
                "HIGH",
                "diagnosis-agent-v1",
                "diagnostic-tools-v1",
                0.0d,
                1.0d,
                DiagnosticExecutionLimits.v1(),
                false);
    }

    public static DiagnosticModelConfiguration runtimeRelease() {
        return releaseBaseline();
    }

    public static DiagnosticModelConfiguration controlled() {
        return new DiagnosticModelConfiguration(
                "controlled",
                "controlled-diagnostic-explainer-v1",
                "OFF",
                "diagnosis-controlled-v1",
                "diagnostic-tools-v1",
                0.0d,
                1.0d,
                DiagnosticExecutionLimits.v1(),
                false);
    }

    public static DiagnosticModelConfiguration deepSeekCandidate(String modelId) {
        if (!candidateModelIds().contains(modelId)) {
            throw new IllegalArgumentException("Unsupported DeepSeek evaluation candidate: " + modelId);
        }
        DiagnosticModelConfiguration baseline = releaseBaseline();
        return new DiagnosticModelConfiguration(
                baseline.provider(),
                modelId,
                baseline.reasoningTier(),
                baseline.promptVersion(),
                baseline.toolSchemaVersion(),
                baseline.temperature(),
                baseline.topP(),
                baseline.limits(),
                baseline.automaticFallbackEnabled());
    }

    public static List<String> candidateModelIds() {
        return List.of("deepseek-v4-flash", "deepseek-v4-pro");
    }

    public String configurationId() {
        return provider + ":" + modelId + ":" + reasoningTier + ":" + promptVersion + ":"
                + toolSchemaVersion + ":temperature=" + temperature + ":topP=" + topP;
    }

    public boolean supportsMultimodalInput() {
        return false;
    }
}
