package com.yliu22520.iotota.diagnosis;

import java.util.List;
import java.util.Objects;

/** Machine-checkable case definition shared by controlled and real-model evaluation. */
public record DiagnosticEvaluationCase(
        String id,
        Type type,
        String scenario,
        String expectedRootCauseCode,
        boolean retryAllowed,
        List<String> requiredAssertions) {

    public DiagnosticEvaluationCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(expectedRootCauseCode, "expectedRootCauseCode");
        requiredAssertions = List.copyOf(requiredAssertions);
    }

    public enum Type {
        BUSINESS,
        SECURITY
    }
}
