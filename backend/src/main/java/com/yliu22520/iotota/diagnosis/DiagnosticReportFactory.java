package com.yliu22520.iotota.diagnosis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiagnosticReportFactory {

    public DiagnosticReportDocument build(UUID diagnosticTaskId,
                                           StructuredToolResult<UpgradeTaskToolData> taskResult,
                                           StructuredToolResult<DeviceStateToolData> deviceResult,
                                           StructuredToolResult<FirmwareVersionToolData> firmwareResult,
                                           StructuredToolResult<VersionCompatibilityDecision> compatibilityResult,
                                           StructuredToolResult<List<FailureLogToolData>> logsResult,
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

        boolean versionIncompatible = "VERSION_INCOMPATIBLE".equals(task.failureCode()) && !compatibility.compatible();
        String rootCauseCode = versionIncompatible ? "VERSION_INCOMPATIBLE" : "EVIDENCE_CONFLICT";
        String retryReasonCode = versionIncompatible ? compatibility.reasonCode() : "EVIDENCE_CONFLICT";
        String conclusion = versionIncompatible
                ? "Target firmware is incompatible with the device model; retry is forbidden."
                : "The available facts do not support a safe version-incompatibility conclusion; retry is forbidden.";

        List<String> rootCauseRefs = evidence.stream().map(EvidenceRef::evidenceId).toList();
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
                        explanation.text(), List.of(compatibilityResult.evidenceId(), deviceResult.evidenceId(),
                                firmwareResult.evidenceId()))),
                List.of(new DiagnosticReportDocument.ReportItem("NO_DEVICE_WRITE",
                        "The diagnosis only read simulated operations data and did not mutate the device or upgrade task.",
                        List.of())),
                List.of(),
                List.of(),
                evidence,
                rootCauseRefs,
                new DiagnosticReportDocument.RetryEligibility(false, "FORBIDDEN", retryReasonCode,
                        versionIncompatible
                                ? "The target firmware does not support the device model; do not retry this task."
                                : "The evidence is conflicting or incomplete; backend rules forbid retry."),
                versionIncompatible
                        ? "Correct the device-to-firmware compatibility mapping and create a new upgrade task."
                        : "Collect consistent evidence before considering any new operation.",
                List.of("trace:" + diagnosticTaskId + ":state", "trace:" + diagnosticTaskId + ":tools",
                        "trace:" + diagnosticTaskId + ":rule", "trace:" + diagnosticTaskId + ":report"),
                new DiagnosticReportDocument.ReportProvenance(modelId, "diagnosis-version-incompatible-v1",
                        "diagnostic-report-v1", generatedAt));
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
                List.of(new DiagnosticReportDocument.ReportItem("EVIDENCE_GAP", error,
                        List.of(failedTool.evidenceId()))),
                List.of(new DiagnosticReportDocument.ReportItem("SOURCE_UNAVAILABLE", error,
                        List.of(failedTool.evidenceId()))),
                List.of(gap),
                List.of(failedTool.evidenceId()),
                new DiagnosticReportDocument.RetryEligibility(false, "FORBIDDEN", "EVIDENCE_INCOMPLETE",
                        "Retry is forbidden while required evidence is unavailable."),
                "Restore the evidence source and start a new diagnosis.",
                List.of("trace:" + diagnosticTaskId + ":evidence-gap"),
                new DiagnosticReportDocument.ReportProvenance(modelId, "diagnosis-version-incompatible-v1",
                        "diagnostic-report-v1", generatedAt));
    }

    private EvidenceRef ref(StructuredToolResult<?> result, String summary) {
        return new EvidenceRef(result.evidenceId(), result.source(), result.observedAt(), summary);
    }
}
