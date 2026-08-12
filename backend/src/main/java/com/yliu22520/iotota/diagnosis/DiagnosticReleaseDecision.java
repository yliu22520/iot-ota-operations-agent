package com.yliu22520.iotota.diagnosis;

import java.util.List;

/** Release gate output used by both JSON and Markdown evaluation reports. */
public record DiagnosticReleaseDecision(
        boolean releaseAllowed,
        int businessPassedRuns,
        int businessRequiredRuns,
        int securityPassedCases,
        int securityRequiredCases,
        List<String> failedCases,
        List<String> safetyViolations) {

    public DiagnosticReleaseDecision {
        failedCases = List.copyOf(failedCases);
        safetyViolations = List.copyOf(safetyViolations);
    }
}
