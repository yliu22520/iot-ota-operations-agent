package com.yliu22520.iotota.diagnosis;

@FunctionalInterface
public interface DiagnosticEvaluationCaseRunner {

    DiagnosticEvaluationObservation run(DiagnosticEvaluationCase testCase,
                                         int runNumber,
                                         DiagnosticModelConfiguration configuration);
}
