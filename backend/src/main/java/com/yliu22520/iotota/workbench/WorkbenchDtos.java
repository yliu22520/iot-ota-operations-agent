package com.yliu22520.iotota.workbench;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkbenchDtos {

    private WorkbenchDtos() {
    }

    public record PublicSummaryList(List<PublicSummary> items) {
    }

    public record PublicSummary(String caseId,
                                String title,
                                String scenario,
                                String conclusion,
                                String confidence,
                                List<String> evidence,
                                String actionOutcome,
                                boolean simulated) {
    }

    public record TaskList(List<TaskSummary> items, boolean simulated) {
    }

    public record TaskSummary(UUID id,
                              String deviceId,
                              String deviceSerialNumber,
                              String targetVersion,
                              Instant failedAt,
                              String status,
                              String failureCode,
                              String failureSummary,
                              String diagnosticStatus,
                              UUID diagnosticTaskId,
                              boolean simulated) {
    }

    public record Telemetry(UUID diagnosticTaskId,
                            ModelConfiguration modelConfiguration,
                            Budget budget,
                            long elapsedMs,
                            List<ToolEvent> toolEvents,
                            TokenUsage tokenUsage,
                            List<StructuredError> errors) {
    }

    public record ModelConfiguration(String provider,
                                     String modelId,
                                     String reasoningTier,
                                     String promptVersion,
                                     String toolSchemaVersion,
                                     double temperature,
                                     double topP,
                                     String reportSchemaVersion,
                                     String embeddingModelId,
                                     String embeddingModelRevision,
                                     String embeddingModelSha256,
                                     boolean automaticFallbackEnabled) {
    }

    public record Budget(int maxToolCalls,
                         int maxModelInteractions,
                         long maxDurationMs,
                         int maxOutputTokens,
                         int maxContextTokens,
                         int maxReadToolRetries) {
    }

    public record ToolEvent(String toolName,
                            String evidenceId,
                            String source,
                            Instant observedAt,
                            long durationMs,
                            String result,
                            String errorCode) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TokenUsage(Integer inputTokens,
                             Integer outputTokens,
                             boolean reported) {
    }

    public record StructuredError(String code, String message, Instant occurredAt) {
    }
}
