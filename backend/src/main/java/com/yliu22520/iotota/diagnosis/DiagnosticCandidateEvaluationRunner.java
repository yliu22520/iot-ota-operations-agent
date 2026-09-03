package com.yliu22520.iotota.diagnosis;

import java.util.List;
import java.util.Objects;

/** Runs the fixed 15-case suite against each explicitly pinned candidate. */
public final class DiagnosticCandidateEvaluationRunner {

    private final DiagnosticReleaseGate releaseGate;

    public DiagnosticCandidateEvaluationRunner() {
        this(new DiagnosticReleaseGate());
    }

    public DiagnosticCandidateEvaluationRunner(DiagnosticReleaseGate releaseGate) {
        this.releaseGate = Objects.requireNonNull(releaseGate, "releaseGate");
    }

    public List<DiagnosticEvaluationReport> run(DiagnosticEvaluationCaseRunner caseRunner) {
        Objects.requireNonNull(caseRunner, "caseRunner");
        return DiagnosticModelConfiguration.candidateModelIds().stream()
                .map(modelId -> new DiagnosticEvaluationRunner(caseRunner, releaseGate)
                        .run(DiagnosticModelConfiguration.evaluationCandidate(modelId)))
                .toList();
    }
}
