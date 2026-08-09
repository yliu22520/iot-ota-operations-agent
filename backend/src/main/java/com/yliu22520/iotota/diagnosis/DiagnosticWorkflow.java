package com.yliu22520.iotota.diagnosis;

import com.yliu22520.iotota.audit.AuditService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class DiagnosticWorkflow {

    private final DiagnosticStateService stateService;
    private final DiagnosticToolset toolset;
    private final DiagnosticChatModel chatModel;
    private final DiagnosticReportFactory reportFactory;
    private final AuditService auditService;
    private final DiagnosticTaskRepository diagnosticTaskRepository;
    private final Clock clock;

    public DiagnosticWorkflow(DiagnosticStateService stateService,
                              DiagnosticToolset toolset,
                              DiagnosticChatModel chatModel,
                              DiagnosticReportFactory reportFactory,
                              AuditService auditService,
                              DiagnosticTaskRepository diagnosticTaskRepository,
                              Clock clock) {
        this.stateService = stateService;
        this.toolset = toolset;
        this.chatModel = chatModel;
        this.reportFactory = reportFactory;
        this.auditService = auditService;
        this.diagnosticTaskRepository = diagnosticTaskRepository;
        this.clock = clock;
    }

    public void execute(UUID diagnosticTaskId, String actor) {
        try {
            stateService.transition(diagnosticTaskId, DiagnosticState.INVESTIGATING, actor);

            StructuredToolResult<UpgradeTaskToolData> task = toolset.getUpgradeTask(
                    getUpgradeTaskId(diagnosticTaskId));
            recordToolCall(diagnosticTaskId, actor, task);
            requireSuccess(task);

            StructuredToolResult<DeviceStateToolData> device = toolset.getDeviceState(task.data().deviceId());
            recordToolCall(diagnosticTaskId, actor, device);
            requireSuccess(device);

            StructuredToolResult<FirmwareVersionToolData> firmware = toolset.getFirmwareVersion(
                    task.data().firmwareVersionId());
            recordToolCall(diagnosticTaskId, actor, firmware);
            requireSuccess(firmware);

            StructuredToolResult<VersionCompatibilityDecision> compatibility = toolset.getVersionCompatibility(
                    task.data().id());
            recordToolCall(diagnosticTaskId, actor, compatibility);
            requireSuccess(compatibility);

            StructuredToolResult<java.util.List<FailureLogToolData>> logs = toolset.getFailureLogs(task.data().id());
            recordToolCall(diagnosticTaskId, actor, logs);
            requireSuccess(logs);

            VersionCompatibilityFacts facts = new VersionCompatibilityFacts(device.data().model(),
                    device.data().currentVersion(), firmware.data().version(), firmware.data().releaseStatus(),
                    firmware.data().compatibleModels());
            DiagnosticExplanation explanation = chatModel.explain(new DiagnosticExplanationRequest(
                    facts, compatibility.data(), java.util.List.of(
                            new EvidenceRef(task.evidenceId(), task.source(), task.observedAt(), "upgrade task"),
                            new EvidenceRef(device.evidenceId(), device.source(), device.observedAt(), "device state"),
                            new EvidenceRef(firmware.evidenceId(), firmware.source(), firmware.observedAt(), "firmware version"),
                            new EvidenceRef(compatibility.evidenceId(), compatibility.source(), compatibility.observedAt(),
                                    "backend compatibility rule"))));

            DiagnosticReportDocument report = reportFactory.build(diagnosticTaskId, task, device, firmware,
                    compatibility, logs, explanation, chatModel.modelId(), Instant.now(clock));
            stateService.persistReportAndMarkReady(diagnosticTaskId, report, actor);
            stateService.transition(diagnosticTaskId, DiagnosticState.COMPLETED, actor);
            auditService.append(actor, "DIAGNOSTIC_TASK", diagnosticTaskId.toString(),
                    "DIAGNOSTIC_COMPLETED", "SUCCESS", diagnosticTaskId.toString(),
                    "Diagnostic reached terminal state", Map.of("state", DiagnosticState.COMPLETED.name()));
        } catch (DiagnosticEvidenceUnavailableException exception) {
            completeAsIncomplete(diagnosticTaskId, actor, exception.result());
        } catch (Exception exception) {
            StructuredToolResult<Object> workflowFailure = StructuredToolResult.failure(
                    "diagnosticWorkflow", "workflow:" + diagnosticTaskId, "diagnosis.workflow",
                    Instant.now(clock), "Diagnostic workflow failed before a complete report was generated");
            completeAsIncomplete(diagnosticTaskId, actor, workflowFailure);
        }
    }

    private UUID getUpgradeTaskId(UUID diagnosticTaskId) {
        return diagnosticTaskRepository.findById(diagnosticTaskId)
                .orElseThrow(() -> new DiagnosticTaskNotFoundException(diagnosticTaskId))
                .getUpgradeTask().getId();
    }

    private void recordToolCall(UUID diagnosticTaskId, String actor, StructuredToolResult<?> result) {
        auditService.append(actor, "DIAGNOSTIC_TASK", diagnosticTaskId.toString(), "TOOL_CALLED",
                result.success() ? "SUCCESS" : "FAILED", diagnosticTaskId.toString(),
                "Structured diagnostic tool call completed", Map.of(
                        "toolName", result.toolName(),
                        "evidenceId", result.evidenceId(),
                        "source", result.source(),
                        "observedAt", result.observedAt().toString()));
    }

    private void requireSuccess(StructuredToolResult<?> result) {
        if (!result.success()) {
            throw new DiagnosticEvidenceUnavailableException(result);
        }
    }

    private void completeAsIncomplete(UUID diagnosticTaskId, String actor, StructuredToolResult<?> failedTool) {
        try {
            DiagnosticReportDocument report = reportFactory.buildIncomplete(diagnosticTaskId, failedTool,
                    chatModel.modelId(), Instant.now(clock));
            stateService.persistIncompleteReport(diagnosticTaskId, report, actor);
            auditService.append(actor, "DIAGNOSTIC_TASK", diagnosticTaskId.toString(),
                    "DIAGNOSTIC_INCOMPLETE", "INCOMPLETE", diagnosticTaskId.toString(),
                    "Diagnostic stopped with an evidence gap", Map.of("state", DiagnosticState.INCOMPLETE.name(),
                            "evidenceId", failedTool.evidenceId()));
        } catch (Exception ignored) {
            // A terminal audit/report write cannot make a safe read-only workflow unsafe.
        }
    }
}
