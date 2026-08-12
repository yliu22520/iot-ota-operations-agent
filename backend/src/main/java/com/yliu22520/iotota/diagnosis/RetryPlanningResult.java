package com.yliu22520.iotota.diagnosis;

public record RetryPlanningResult(
        DiagnosticReportDocument.RetryEligibility eligibility,
        RetryPlanDocument plan) {
}
