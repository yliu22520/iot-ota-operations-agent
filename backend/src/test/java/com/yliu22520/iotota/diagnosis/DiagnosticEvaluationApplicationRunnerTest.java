package com.yliu22520.iotota.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticEvaluationApplicationRunnerTest {

    @Test
    void explicitRunnerWritesOnePairOfArtifactsPerCandidate(@TempDir Path outputDirectory) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DiagnosticModelClient client = request -> {
            calls.incrementAndGet();
            return new DiagnosticModelResponse("""
                    {
                      "rootCauseCode": "VERSION_INCOMPATIBLE",
                      "retryEligible": false,
                      "approvalRequired": false,
                      "executionVerified": false,
                      "assertions": ["RULE_EVIDENCE_REFERENCED"],
                      "actions": [],
                      "toolCalls": 5
                    }
                    """, 100, 20);
        };

        DiagnosticEvaluationApplicationRunner runner = new DiagnosticEvaluationApplicationRunner(
                client, new ObjectMapper(), outputDirectory, java.time.Clock.systemUTC());

        runner.run(new DefaultApplicationArguments());

        assertThat(calls).hasValue(90);
        try (Stream<Path> files = Files.list(outputDirectory)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder(
                            "deepseek_deepseek-v4-flash_HIGH_diagnosis-agent-v1_diagnostic-tools-v1_temperature_0.0_topP_1.0.json",
                            "deepseek_deepseek-v4-flash_HIGH_diagnosis-agent-v1_diagnostic-tools-v1_temperature_0.0_topP_1.0.md",
                            "deepseek_deepseek-v4-pro_HIGH_diagnosis-agent-v1_diagnostic-tools-v1_temperature_0.0_topP_1.0.json",
                            "deepseek_deepseek-v4-pro_HIGH_diagnosis-agent-v1_diagnostic-tools-v1_temperature_0.0_topP_1.0.md");
        }
    }
}
