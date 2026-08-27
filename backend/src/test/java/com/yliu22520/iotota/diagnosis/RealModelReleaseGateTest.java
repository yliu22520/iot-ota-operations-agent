package com.yliu22520.iotota.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit, opt-in real-provider release gate.
 *
 * <p>Normal unit/PR CI skips this test. A release validation must set
 * RUN_REAL_MODEL_EVALUATION=true and provide DEEPSEEK_API_KEY.</p>
 */
class RealModelReleaseGateTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_REAL_MODEL_EVALUATION", matches = "(?i)true")
    void bothPinnedCandidatesMustPassTheRealReleaseGate() throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assertNotNull(apiKey, "DEEPSEEK_API_KEY is required for real-model release validation");
        assertFalse(apiKey.isBlank(), "DEEPSEEK_API_KEY is required for real-model release validation");

        String baseUrl = environmentOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com");
        DiagnosticModelConfiguration baseline = DiagnosticModelConfiguration.releaseBaseline();

        DeepSeekApi api = DeepSeekApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(baseline.modelId())
                .temperature(baseline.temperature())
                .topP(baseline.topP())
                .maxTokens(baseline.limits().maxOutputTokens())
                .build();
        ChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DiagnosticModelClient client = new SpringAiDeepSeekModelClient(chatModel);
        DeepSeekEvaluationCaseRunner caseRunner = new DeepSeekEvaluationCaseRunner(client, Clock.systemUTC(), objectMapper);
        List<DiagnosticEvaluationReport> reports = new DiagnosticCandidateEvaluationRunner().run(caseRunner);

        Path outputDirectory = Path.of(environmentOrDefault(
                "DIAGNOSTIC_EVALUATION_OUTPUT_DIRECTORY",
                "backend/target/real-model-evaluation"));
        DiagnosticEvaluationReportWriter writer = new DiagnosticEvaluationReportWriter(objectMapper);
        for (DiagnosticEvaluationReport report : reports) {
            writer.write(report, outputDirectory);
        }

        String failures = reports.stream()
                .filter(report -> !report.releaseDecision().releaseAllowed())
                .map(report -> report.modelId()
                        + " failedCases=" + report.releaseDecision().failedCases()
                        + " safetyViolations=" + report.releaseDecision().safetyViolations())
                .collect(Collectors.joining("; "));

        assertTrue(reports.stream().allMatch(report -> report.releaseDecision().releaseAllowed()),
                () -> "Real-model release gate failed: " + failures);
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
