package com.yliu22520.iotota.diagnosis;

import com.yliu22520.iotota.audit.AuditService;
import com.yliu22520.iotota.knowledge.KnowledgeSearchResult;
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
    private final RetryPlanPort retryPlanPort;
    private final Clock clock;

    public DiagnosticWorkflow(DiagnosticStateService stateService,
                              DiagnosticToolset toolset,
                              DiagnosticChatModel chatModel,
                              DiagnosticReportFactory reportFactory,
                              AuditService auditService,
                              DiagnosticTaskRepository diagnosticTaskRepository,
                              RetryPlanPort retryPlanPort,
                              Clock clock) {
        this.stateService = stateService;
        this.toolset = toolset;
        this.chatModel = chatModel;
        this.reportFactory = reportFactory;
        this.auditService = auditService;
        this.diagnosticTaskRepository = diagnosticTaskRepository;
        this.retryPlanPort = retryPlanPort;
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

            StructuredToolResult<KnowledgeSearchResult> knowledge = toolset.searchKnowledge(task.data().id());
            recordToolCall(diagnosticTaskId, actor, knowledge);

            if ("CALLBACK_TIMEOUT".equals(task.data().failureCode())) {
                executeCallbackTimeoutPath(diagnosticTaskId, actor, task, device, firmware, compatibility, logs,
                        knowledge);
                return;
            }

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
                    compatibility, logs, knowledge, explanation, chatModel.modelId(), Instant.now(clock));
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

    private void executeCallbackTimeoutPath(
            UUID diagnosticTaskId,
            String actor,
            StructuredToolResult<UpgradeTaskToolData> task,
            StructuredToolResult<DeviceStateToolData> device,
            StructuredToolResult<FirmwareVersionToolData> firmware,
            StructuredToolResult<VersionCompatibilityDecision> compatibility,
            StructuredToolResult<java.util.List<FailureLogToolData>> logs,
            StructuredToolResult<KnowledgeSearchResult> knowledge) {
        StructuredToolResult<java.util.List<MessageStateToolData>> messages =
                toolset.getMessageStates(task.data().id());
        recordToolCall(diagnosticTaskId, actor, messages);
        requireSuccess(messages);
        if (messages.data().isEmpty()) {
            throw new DiagnosticEvidenceUnavailableException(StructuredToolResult.failure(
                    "getMessageStates", messages.evidenceId(), messages.source(), messages.observedAt(),
                    "No message state was recorded for the callback timeout"));
        }
        MessageStateToolData latestMessage = messages.data().get(messages.data().size() - 1);
        java.util.List<String> evidenceRefs = java.util.List.of(task.evidenceId(), device.evidenceId(),
                firmware.evidenceId(), compatibility.evidenceId(), logs.evidenceId(), messages.evidenceId());
        RetryPlanningResult planning = retryPlanPort.evaluateAndCreate(new RetryPlanRequest(
                diagnosticTaskId, task.data().id(), task.data().taskVersion(), task.data().status(),
                task.data().failureCode(), device.data().online(), compatibility.data().compatible(),
                task.data().retryCount(), task.data().maxRetries(), 1, latestMessage.sendStatus(),
                latestMessage.callbackStatus(), firmware.data().version(), evidenceRefs, actor));

        DiagnosticReportDocument report = reportFactory.buildCallbackTimeout(diagnosticTaskId, task, device,
                firmware, compatibility, logs, messages, knowledge, planning, chatModel.modelId(), Instant.now(clock));
        stateService.persistReportAndMarkReady(diagnosticTaskId, report, actor);
        if (planning.plan() != null) {
            stateService.transition(diagnosticTaskId, DiagnosticState.WAITING_APPROVAL, actor);
            auditService.append(actor, "RETRY_PLAN", planning.plan().planId().toString(),
                    "RETRY_PLAN_CREATED", "PENDING_APPROVAL", diagnosticTaskId.toString(),
                    "A fifteen-minute retry plan was bound to the current task snapshot", Map.of(
                            "planVersion", planning.plan().planVersion(),
                            "expiresAt", planning.plan().expiresAt().toString(),
                            "upgradeTaskId", planning.plan().upgradeTaskId().toString()));
        } else {
            stateService.transition(diagnosticTaskId, DiagnosticState.COMPLETED, actor);
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
