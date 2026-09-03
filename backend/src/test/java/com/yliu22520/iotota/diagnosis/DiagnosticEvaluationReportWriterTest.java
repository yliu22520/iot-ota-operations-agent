package com.yliu22520.iotota.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticEvaluationReportWriterTest {

    @Test
    void writesMachineReadableAndHumanReadableArtifactsWithoutRawReasoning(@TempDir Path outputDirectory)
            throws Exception {
        DiagnosticEvaluationCase testCase = DiagnosticEvaluationCaseCatalog.all().get(0);
        DiagnosticEvaluationObservation observation = new DiagnosticEvaluationObservation(
                testCase.id(), testCase.type(), 1, true, List.of(), null, 5, 1, 100, 20, 100,
                "rootCauseCode=VERSION_INCOMPATIBLE;retryEligible=false;approvalRequired=false");
        DiagnosticEvaluationReport report = new DiagnosticEvaluationReport(
                "gemini:gemini-3.1-flash-lite:HIGH:diagnosis-agent-v2:diagnostic-tools-v1:temperature=0.0:topP=1.0",
                "gemini-3.1-flash-lite", "HIGH", "diagnosis-agent-v2", "diagnostic-tools-v1",
                List.of(observation), new DiagnosticReleaseDecision(true, 3, 3, 5, 5, List.of(), List.of()));

        DiagnosticEvaluationReportFiles files = new DiagnosticEvaluationReportWriter(new ObjectMapper())
                .write(report, outputDirectory);

        assertThat(files.json()).exists();
        assertThat(files.markdown()).exists();
        assertThat(Files.readString(files.json())).contains("\"provider\"", "\"promptId\"", "\"toolSchemaId\"",
                "gemini", "gemini-3.1-flash-lite", "VERSION_INCOMPATIBLE",
                "retryAllowed", "requiredAssertions", "automaticFallbackEnabled", "maxToolCalls")
                .doesNotContain("reasoning_content", "raw prompt");
        assertThat(Files.readString(files.markdown())).contains("Provider: `gemini`", "Prompt ID: `diagnosis-agent`",
                "Prompt version", "Tool schema ID: `diagnostic-tools`", "Tool schema version", "Expected root cause",
                "Expected retry",
                "RULE_EVIDENCE_REFERENCED", "VERSION_INCOMPATIBLE", "rootCauseCode=VERSION_INCOMPATIBLE")
                .doesNotContain("reasoning_content");
    }
}
