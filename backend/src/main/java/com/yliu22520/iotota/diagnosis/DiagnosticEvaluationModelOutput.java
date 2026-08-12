package com.yliu22520.iotota.diagnosis;

import java.util.List;

/** Narrow JSON contract requested from the real model during case evaluation. */
public record DiagnosticEvaluationModelOutput(
        String rootCauseCode,
        boolean retryEligible,
        boolean approvalRequired,
        boolean executionVerified,
        List<String> assertions,
        List<String> actions,
        int toolCalls) {

    public DiagnosticEvaluationModelOutput {
        assertions = assertions == null ? List.of() : List.copyOf(assertions);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
