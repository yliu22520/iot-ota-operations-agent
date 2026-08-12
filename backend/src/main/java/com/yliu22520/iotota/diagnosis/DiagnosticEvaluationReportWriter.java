package com.yliu22520.iotota.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Writes safe evaluation artifacts without prompts, raw provider messages, or chain-of-thought. */
public final class DiagnosticEvaluationReportWriter {

    private final ObjectMapper objectMapper;

    public DiagnosticEvaluationReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy().findAndRegisterModules();
    }

    public DiagnosticEvaluationReportFiles write(DiagnosticEvaluationReport report, Path outputDirectory)
            throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Files.createDirectories(outputDirectory);

        String baseName = safeFileName(report.configurationId());
        Path json = outputDirectory.resolve(baseName + ".json");
        Path markdown = outputDirectory.resolve(baseName + ".md");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(json.toFile(), report);
        Files.writeString(markdown, renderMarkdown(report), StandardCharsets.UTF_8);
        return new DiagnosticEvaluationReportFiles(json, markdown);
    }

    private String renderMarkdown(DiagnosticEvaluationReport report) {
        Map<String, DiagnosticEvaluationCase> cases = report.cases().stream()
                .collect(Collectors.toMap(DiagnosticEvaluationCase::id, Function.identity()));
        StringBuilder markdown = new StringBuilder()
                .append("# Diagnostic evaluation\n\n")
                .append("- Configuration: `").append(report.configurationId()).append("`\n")
                .append("- Model: `").append(report.modelId()).append("`\n")
                .append("- Reasoning tier: `").append(report.reasoningTier()).append("`\n")
                .append("- Prompt version: `").append(report.promptVersion()).append("`\n")
                .append("- Tool schema version: `").append(report.toolSchemaVersion()).append("`\n")
                .append("- Automatic fallback: `").append(report.automaticFallbackEnabled()).append("`\n")
                .append("- Budget: `").append(report.executionLimits()).append("`\n")
                .append("- Pass rate: ").append(report.passRate()).append("\n")
                .append("- Release allowed: `").append(report.releaseDecision().releaseAllowed()).append("`\n\n")
                .append("## Case runs\n\n")
                .append("| Case | Type | Expected root cause | Expected retry | Required assertions | Run | Passed | Actual outcome | Failure | "
                        + "Tool calls | Model interactions | Input tokens | Output tokens | Duration ms |\n")
                .append("|---|---|---|---:|---|---:|---:|---|---|---:|---:|---:|---:|---:|\n");
        for (DiagnosticEvaluationObservation observation : report.observations()) {
            DiagnosticEvaluationCase testCase = cases.get(observation.caseId());
            markdown.append('|').append(observation.caseId()).append('|').append(observation.type()).append('|')
                    .append(testCase == null ? "UNKNOWN" : testCase.expectedRootCauseCode()).append('|')
                    .append(testCase == null ? "UNKNOWN" : testCase.retryAllowed()).append('|')
                    .append(testCase == null ? "UNKNOWN" : String.join(", ", testCase.requiredAssertions())).append('|')
                    .append(observation.runNumber()).append('|').append(observation.passed()).append('|')
                    .append(observation.actualOutcome()).append('|')
                    .append(observation.failureClass() == null ? "" : observation.failureClass()).append('|')
                    .append(observation.toolCalls()).append('|').append(observation.modelInteractions()).append('|')
                    .append(observation.inputTokens()).append('|').append(observation.outputTokens()).append('|')
                    .append(observation.durationMs()).append('|').append('\n');
        }
        return markdown.toString();
    }

    private String safeFileName(String configurationId) {
        return configurationId.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
