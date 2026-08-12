package com.yliu22520.iotota.diagnosis;

import com.yliu22520.iotota.knowledge.KnowledgeEvidence;
import com.yliu22520.iotota.knowledge.KnowledgeSearchResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiagnosticReportFactory {

    private final String embeddingModelId;
    private final String embeddingModelRevision;
    private final String embeddingModelSha256;

    public DiagnosticReportFactory(String embeddingModelId,
                                   String embeddingModelRevision,
                                   String embeddingModelSha256) {
        this.embeddingModelId = embeddingModelId;
        this.embeddingModelRevision = embeddingModelRevision;
        this.embeddingModelSha256 = embeddingModelSha256;
    }

    public DiagnosticReportDocument build(UUID diagnosticTaskId,
                                           StructuredToolResult<UpgradeTaskToolData> taskResult,
                                           StructuredToolResult<DeviceStateToolData> deviceResult,
                                           StructuredToolResult<FirmwareVersionToolData> firmwareResult,
                                           StructuredToolResult<VersionCompatibilityDecision> compatibilityResult,
                                           StructuredToolResult<List<FailureLogToolData>> logsResult,
                                           StructuredToolResult<KnowledgeSearchResult> knowledgeResult,
                                           DiagnosticExplanation explanation,
                                           String modelId,
                                           Instant generatedAt) {
        UpgradeTaskToolData task = taskResult.data();
        DeviceStateToolData device = deviceResult.data();
        FirmwareVersionToolData firmware = firmwareResult.data();
        VersionCompatibilityDecision compatibility = compatibilityResult.data();

        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(ref(taskResult, "Upgrade task " + task.id() + " reported " + task.failureCode()));
        evidence.add(ref(deviceResult, "Device model is " + device.model() + " at version " + device.currentVersion()));
        evidence.add(ref(firmwareResult, "Target firmware " + firmware.version() + " supports "
                + firmware.compatibleModels()));
        evidence.add(ref(compatibilityResult, "Backend rule returned " + compatibility.reasonCode()));
        if (logsResult.success()) {
            evidence.add(ref(logsResult, "Failure log count: " + logsResult.data().size()));
        }
        List<String> rootCauseRefs = evidence.stream().map(EvidenceRef::evidenceId).toList();
        appendKnowledgeEvidence(evidence, knowledgeResult);

        boolean versionIncompatible = "VERSION_INCOMPATIBLE".equals(task.failureCode()) && !compatibility.compatible();
        String rootCauseCode = versionIncompatible ? "VERSION_INCOMPATIBLE" : "EVIDENCE_CONFLICT";
        String retryReasonCode = versionIncompatible ? compatibility.reasonCode() : "EVIDENCE_CONFLICT";
        String conclusion = versionIncompatible
                ? "Target firmware is incompatible with the device model; retry is forbidden."
                : "The available facts do not support a safe version-incompatibility conclusion; retry is forbidden.";

        return new DiagnosticReportDocument(
                1,
                diagnosticTaskId,
                rootCauseCode,
                conclusion,
                List.of(
                        new DiagnosticReportDocument.ReportItem("TASK_FAILURE",
                                "Upgrade task " + task.id() + " is in " + task.status()
                                        + " with failure code " + task.failureCode(),
                                List.of(taskResult.evidenceId())),
                        new DiagnosticReportDocument.ReportItem("DEVICE_STATE",
                                "Device " + device.id() + " uses model " + device.model()
                                        + " and current version " + device.currentVersion(),
                                List.of(deviceResult.evidenceId())),
                        new DiagnosticReportDocument.ReportItem("TARGET_FIRMWARE",
                                "Firmware " + firmware.version() + " declares compatible models "
                                        + firmware.compatibleModels(), List.of(firmwareResult.evidenceId()))),
                List.of(new DiagnosticReportDocument.ReportItem(compatibility.reasonCode(),
                        "Deterministic backend compatibility rule returned " + compatibility.reasonCode() + ".",
                        List.of(compatibilityResult.evidenceId(), deviceResult.evidenceId(),
                                firmwareResult.evidenceId()))),
                List.of(new DiagnosticReportDocument.ReportItem("MODEL_EXPLANATION",
                        explanation.text(), List.of(compatibilityResult.evidenceId()))),
                knowledgeSuggestions(knowledgeResult),
                List.of(new DiagnosticReportDocument.ReportItem("NO_DEVICE_WRITE",
                        "The diagnosis only read simulated operations data and did not mutate the device or upgrade task.",
                        List.of())),
                List.of(),
                knowledgeGaps(knowledgeResult),
                evidence,
                rootCauseRefs,
                new DiagnosticReportDocument.RetryEligibility(false, "FORBIDDEN", retryReasonCode,
                        versionIncompatible
                                ? "The target firmware does not support the device model; do not retry this task."
                                : "The evidence is conflicting or incomplete; backend rules forbid retry."),
                null,
                versionIncompatible
                        ? "Correct the device-to-firmware compatibility mapping and create a new upgrade task."
                        : "Collect consistent evidence before considering any new operation.",
                List.of("trace:" + diagnosticTaskId + ":state", "trace:" + diagnosticTaskId + ":tools",
                        "trace:" + diagnosticTaskId + ":rule", "trace:" + diagnosticTaskId + ":report"),
                provenance(modelId, "diagnosis-version-incompatible-v1", generatedAt));
    }

    public DiagnosticReportDocument buildIncomplete(UUID diagnosticTaskId,
                                                     StructuredToolResult<?> failedTool,
                                                     String modelId,
                                                     Instant generatedAt) {
        String error = failedTool.error() == null ? "tool did not return structured data" : failedTool.error();
        EvidenceRef gap = ref(failedTool, "Unavailable evidence source: " + error);
        return new DiagnosticReportDocument(
                1,
                diagnosticTaskId,
                "EVIDENCE_INCOMPLETE",
                "Diagnosis is incomplete because a required evidence source was unavailable.",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new DiagnosticReportDocument.ReportItem("EVIDENCE_GAP", error,
                        List.of(failedTool.evidenceId()))),
                List.of(new DiagnosticReportDocument.ReportItem("SOURCE_UNAVAILABLE", error,
                        List.of(failedTool.evidenceId()))),
                List.of(gap),
                List.of(failedTool.evidenceId()),
                new DiagnosticReportDocument.RetryEligibility(false, "FORBIDDEN", "EVIDENCE_INCOMPLETE",
                        "Retry is forbidden while required evidence is unavailable."),
                null,
                "Restore the evidence source and start a new diagnosis.",
                List.of("trace:" + diagnosticTaskId + ":evidence-gap"),
                provenance(modelId, "diagnosis-version-incompatible-v1", generatedAt));
    }

    public DiagnosticReportDocument buildCallbackTimeout(
            UUID diagnosticTaskId,
            StructuredToolResult<UpgradeTaskToolData> taskResult,
            StructuredToolResult<DeviceStateToolData> deviceResult,
            StructuredToolResult<FirmwareVersionToolData> firmwareResult,
            StructuredToolResult<VersionCompatibilityDecision> compatibilityResult,
            StructuredToolResult<List<FailureLogToolData>> logsResult,
            StructuredToolResult<List<MessageStateToolData>> messagesResult,
            StructuredToolResult<KnowledgeSearchResult> knowledgeResult,
            RetryPlanningResult planning,
            String modelId,
            Instant generatedAt) {
        UpgradeTaskToolData task = taskResult.data();
        MessageStateToolData message = messagesResult.data().get(messagesResult.data().size() - 1);
        List<EvidenceRef> evidence = new ArrayList<>(List.of(
                ref(taskResult, "Upgrade task is a final failure with callback timeout"),
                ref(deviceResult, "Device is online"),
                ref(firmwareResult, "Target firmware is released for the device model"),
                ref(compatibilityResult, "Backend compatibility rule permits the target version"),
                ref(logsResult, "Failure logs contain callback timeout evidence"),
                ref(messagesResult, "Message was sent and callback status is " + message.callbackStatus())));
        List<String> evidenceIds = evidence.stream().map(EvidenceRef::evidenceId).toList();
        appendKnowledgeEvidence(evidence, knowledgeResult);
        return new DiagnosticReportDocument(
                1,
                diagnosticTaskId,
                "CALLBACK_TIMEOUT",
                "The upgrade command was sent but its callback timed out; backend rules allow one approved retry.",
                List.of(
                        new DiagnosticReportDocument.ReportItem("TASK_FINAL_FAILURE",
                                "Upgrade task " + task.id() + " is in FINAL_FAILURE.", List.of(taskResult.evidenceId())),
                        new DiagnosticReportDocument.ReportItem("CALLBACK_TIMEOUT_CONFIRMED",
                                "The command was sent and the callback timed out.",
                                List.of(messagesResult.evidenceId(), logsResult.evidenceId()))),
                List.of(new DiagnosticReportDocument.ReportItem("RETRY_ELIGIBILITY_RULE",
                        "Current task, message, device and version facts support a retryable callback timeout.", evidenceIds)),
                List.of(),
                knowledgeSuggestions(knowledgeResult),
                List.of(),
                List.of(),
                knowledgeGaps(knowledgeResult),
                evidence,
                evidenceIds,
                planning.eligibility(),
                planning.plan(),
                "Review and approve the bound retry plan before it expires.",
                List.of("trace:" + diagnosticTaskId + ":tools", "trace:" + diagnosticTaskId + ":eligibility",
                        "trace:" + diagnosticTaskId + ":plan"),
                provenance(modelId, "diagnosis-callback-timeout-v1", generatedAt));
    }

    private EvidenceRef ref(StructuredToolResult<?> result, String summary) {
        return new EvidenceRef(result.evidenceId(), result.source(), result.observedAt(), summary);
    }

    private void appendKnowledgeEvidence(List<EvidenceRef> evidence,
                                         StructuredToolResult<KnowledgeSearchResult> result) {
        if (!result.success() || result.data() == null) {
            return;
        }
        for (KnowledgeEvidence item : result.data().evidence()) {
            evidence.add(new EvidenceRef(item.evidenceId(), result.source(), result.observedAt(),
                    "Retrieved " + item.documentId() + " " + item.documentVersion()
                            + " section " + item.section() + " chunk " + item.chunkId()));
        }
    }

    private List<DiagnosticReportDocument.ReportItem> knowledgeSuggestions(
            StructuredToolResult<KnowledgeSearchResult> result) {
        if (!result.success() || result.data() == null) {
            return List.of();
        }
        return result.data().evidence().stream()
                .map(item -> new DiagnosticReportDocument.ReportItem("KNOWLEDGE_SUGGESTION", item.content(),
                        List.of(item.evidenceId())))
                .toList();
    }

    private List<DiagnosticReportDocument.ReportItem> knowledgeGaps(
            StructuredToolResult<KnowledgeSearchResult> result) {
        if (!result.success()) {
            String error = result.error() == null ? "Local knowledge retrieval was unavailable." : result.error();
            return List.of(new DiagnosticReportDocument.ReportItem("KNOWLEDGE_RETRIEVAL_FAILED", error,
                    List.of(result.evidenceId())));
        }
        if (result.data() == null || result.data().evidence().isEmpty()) {
            return List.of(new DiagnosticReportDocument.ReportItem("KNOWLEDGE_NOT_FOUND",
                    "No relevant local knowledge was found for this diagnosis.", List.of(result.evidenceId())));
        }
        return List.of();
    }

    private DiagnosticReportDocument.ReportProvenance provenance(String modelId,
                                                                  String promptVersion,
                                                                  Instant generatedAt) {
        return new DiagnosticReportDocument.ReportProvenance(modelId, promptVersion, "diagnostic-report-v1",
                embeddingModelId, embeddingModelRevision, embeddingModelSha256, generatedAt);
    }
}
