package com.yliu22520.iotota.diagnosis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Canonical, machine-readable report shared by the API and the workbench. */
public record DiagnosticReportDocument(
        int schemaVersion,
        UUID diagnosticTaskId,
        String rootCauseCode,
        String conclusion,
        List<ReportItem> facts,
        List<ReportItem> inferences,
        List<ReportItem> exclusions,
        List<ReportItem> unknowns,
        List<ReportItem> evidenceGaps,
        List<EvidenceRef> evidenceRefs,
        List<String> rootCauseEvidenceRefs,
        RetryEligibility retryEligibility,
        String nextAction,
        List<String> decisionTraceRefs,
        ReportProvenance provenance) {

    public record ReportItem(String code, String text, List<String> evidenceRefs) {
    }

    public record RetryEligibility(boolean eligible, String status, String reasonCode, String reason) {
    }

    public record ReportProvenance(String modelId, String promptVersion, String schemaVersion,
                                   Instant generatedAt) {
    }
}
