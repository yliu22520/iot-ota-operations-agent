package com.yliu22520.iotota.workbench;

import com.yliu22520.iotota.audit.AuditEvent;
import com.yliu22520.iotota.audit.AuditEventRepository;
import com.yliu22520.iotota.diagnosis.DiagnosticReport;
import com.yliu22520.iotota.diagnosis.DiagnosticReportDocument;
import com.yliu22520.iotota.diagnosis.DiagnosticReportRepository;
import com.yliu22520.iotota.diagnosis.DiagnosticTask;
import com.yliu22520.iotota.diagnosis.DiagnosticTaskNotFoundException;
import com.yliu22520.iotota.diagnosis.DiagnosticTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkbenchTelemetryService {

    private final DiagnosticTaskRepository diagnosticTaskRepository;
    private final DiagnosticReportRepository diagnosticReportRepository;
    private final AuditEventRepository auditEventRepository;

    public WorkbenchTelemetryService(DiagnosticTaskRepository diagnosticTaskRepository,
                                     DiagnosticReportRepository diagnosticReportRepository,
                                     AuditEventRepository auditEventRepository) {
        this.diagnosticTaskRepository = diagnosticTaskRepository;
        this.diagnosticReportRepository = diagnosticReportRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public WorkbenchDtos.Telemetry get(UUID diagnosticTaskId) {
        DiagnosticTask task = diagnosticTaskRepository.findById(diagnosticTaskId)
                .orElseThrow(() -> new DiagnosticTaskNotFoundException(diagnosticTaskId));
        DiagnosticReportDocument report = diagnosticReportRepository.findByDiagnosticTaskId(diagnosticTaskId)
                .map(DiagnosticReport::getReport)
                .orElse(null);
        List<AuditEvent> events = auditEventRepository
                .findByCorrelationIdOrderByOccurredAtAsc(diagnosticTaskId.toString());

        List<WorkbenchDtos.ToolEvent> toolEvents = events.stream()
                .filter(event -> "TOOL_CALLED".equals(event.getAction()))
                .map(this::toToolEvent)
                .toList();
        List<WorkbenchDtos.StructuredError> errors = new ArrayList<>();
        events.stream()
                .filter(event -> "FAILED".equals(event.getResult()) || "INCOMPLETE".equals(event.getResult()))
                .forEach(event -> errors.add(new WorkbenchDtos.StructuredError(
                        stringMetadata(event.getMetadata(), "errorCode", "TOOL_RESULT_UNAVAILABLE"),
                        event.getSummary(), event.getOccurredAt())));
        WorkbenchDtos.TokenUsage tokenUsage = tokenUsage(events);
        int maxToolCalls = 10;
        int maxModelInteractions = 8;
        long maxDurationMs = 90_000L;
        int maxOutputTokens = 4_096;
        int maxContextTokens = 32_768;
        int maxReadToolRetries = 1;
        return new WorkbenchDtos.Telemetry(
                diagnosticTaskId,
                toModelConfiguration(report),
                new WorkbenchDtos.Budget(maxToolCalls, maxModelInteractions, maxDurationMs, maxOutputTokens,
                        maxContextTokens, maxReadToolRetries),
                elapsedMs(task, events),
                toolEvents,
                tokenUsage,
                List.copyOf(errors));
    }

    private WorkbenchDtos.ModelConfiguration toModelConfiguration(DiagnosticReportDocument report) {
        DiagnosticReportDocument.ReportProvenance provenance = report == null ? null : report.provenance();
        return new WorkbenchDtos.ModelConfiguration(
                "controlled",
                provenance == null ? "controlled-diagnostic-explainer-v1" : provenance.modelId(),
                "CONTROLLED",
                provenance == null ? "diagnosis-controlled-v1" : provenance.promptVersion(),
                "diagnostic-tools-v1",
                0.0d,
                1.0d,
                provenance == null ? "diagnostic-report-v1" : provenance.schemaVersion(),
                provenance == null ? "" : provenance.embeddingModelId(),
                provenance == null ? "" : provenance.embeddingModelRevision(),
                provenance == null ? "" : provenance.embeddingModelSha256(),
                false);
    }

    private WorkbenchDtos.ToolEvent toToolEvent(AuditEvent event) {
        Map<String, Object> metadata = event.getMetadata() == null ? Map.of() : event.getMetadata();
        return new WorkbenchDtos.ToolEvent(
                stringMetadata(metadata, "toolName", "unknown"),
                stringMetadata(metadata, "evidenceId", ""),
                stringMetadata(metadata, "source", ""),
                event.getOccurredAt(),
                longMetadata(metadata, "durationMs"),
                event.getResult(),
                metadata.containsKey("errorCode")
                        ? stringMetadata(metadata, "errorCode", "TOOL_RESULT_UNAVAILABLE") : null);
    }

    private WorkbenchDtos.TokenUsage tokenUsage(List<AuditEvent> events) {
        return events.stream()
                .filter(event -> "MODEL_CALLED".equals(event.getAction()))
                .findFirst()
                .map(event -> {
                    Map<String, Object> metadata = event.getMetadata() == null ? Map.of() : event.getMetadata();
                    Integer input = integerMetadata(metadata, "inputTokens");
                    Integer output = integerMetadata(metadata, "outputTokens");
                    return new WorkbenchDtos.TokenUsage(input, output, Boolean.TRUE.equals(metadata.get("tokenUsageReported")));
                })
                .orElseGet(() -> new WorkbenchDtos.TokenUsage(null, null, false));
    }

    private long elapsedMs(DiagnosticTask task, List<AuditEvent> events) {
        if (events.isEmpty()) {
            return Math.max(0, Duration.between(task.getCreatedAt(), task.getUpdatedAt()).toMillis());
        }
        return Math.max(0, Duration.between(task.getCreatedAt(),
                events.get(events.size() - 1).getOccurredAt()).toMillis());
    }

    private static String stringMetadata(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static long longMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static Integer integerMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
