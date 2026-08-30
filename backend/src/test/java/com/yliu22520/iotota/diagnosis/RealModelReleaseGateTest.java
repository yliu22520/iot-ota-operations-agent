package com.yliu22520.iotota.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.DisposableBean;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit, opt-in real-provider release gate.
 *
 * <p>Normal unit/PR CI skips this test. A release validation must set
 * RUN_REAL_MODEL_EVALUATION=true and provide GEMINI_API_KEY.</p>
 */
class RealModelReleaseGateTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_REAL_MODEL_EVALUATION", matches = "(?i)true")
    void selectedRuntimeBaselineMustPassTheRealReleaseGate() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertNotNull(apiKey, "GEMINI_API_KEY is required for real-model release validation");
        assertFalse(apiKey.isBlank(), "GEMINI_API_KEY is required for real-model release validation");

        String configuredProvider = environmentOrDefault(
                "DIAGNOSTIC_MODEL_PROVIDER", DiagnosticModelConfiguration.GEMINI_PROVIDER);
        String configuredModel = environmentOrDefault(
                "DIAGNOSTIC_MODEL_NAME", DiagnosticModelConfiguration.RELEASE_MODEL_ID);
        assertEquals(DiagnosticModelConfiguration.GEMINI_PROVIDER,
                configuredProvider.trim().toLowerCase(Locale.ROOT),
                "Real-model release validation must use the Gemini provider");
        assertEquals(DiagnosticModelConfiguration.RELEASE_MODEL_ID, configuredModel.trim(),
                "Real-model release validation must use the pinned model");
        DiagnosticModelConfiguration baseline = new DiagnosisConfiguration()
                .diagnosticModelConfiguration(configuredProvider, configuredModel);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DiagnosticModelClient client = GoogleGenAiDiagnosticModelClientFactory.fromApiKey(apiKey, baseline);
        try {
            RealModelEvaluationCaseRunner caseRunner = new RealModelEvaluationCaseRunner(client, Clock.systemUTC(), objectMapper);
            List<DiagnosticEvaluationReport> reports = new DiagnosticCandidateEvaluationRunner().run(caseRunner);

            assertEquals(DiagnosticModelConfiguration.candidateModelIds().size(), reports.size(),
                    "Every pinned candidate must produce an evaluation report");

            Path outputDirectory = Path.of(environmentOrDefault(
                    "DIAGNOSTIC_EVALUATION_OUTPUT_DIRECTORY",
                    "backend/target/real-model-evaluation"));
            DiagnosticEvaluationReportWriter writer = new DiagnosticEvaluationReportWriter(objectMapper);
            for (DiagnosticEvaluationReport report : reports) {
                writer.write(report, outputDirectory);
            }

            String runtimeModelId = DiagnosticModelConfiguration.runtimeRelease().modelId();
            DiagnosticEvaluationReport runtimeReport = reports.stream()
                    .filter(report -> runtimeModelId.equals(report.modelId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing evaluation report for runtime baseline " + runtimeModelId));

            assertTrue(runtimeReport.releaseDecision().releaseAllowed(),
                    () -> "Runtime baseline release gate failed: model=" + runtimeModelId
                            + " failedCases=" + runtimeReport.releaseDecision().failedCases()
                            + " safetyViolations=" + runtimeReport.releaseDecision().safetyViolations());
        } finally {
            if (client instanceof DisposableBean disposable) {
                disposable.destroy();
            }
        }
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
