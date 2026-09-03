package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticCandidateEvaluationRunnerTest {

    @Test
    void evaluatesEachPinnedCandidateWithTheSameFortyFiveRunSuite() {
        AtomicInteger calls = new AtomicInteger();
        List<String> modelIds = new ArrayList<>();
        DiagnosticEvaluationCaseRunner caseRunner = (testCase, runNumber, configuration) -> {
            calls.incrementAndGet();
            modelIds.add(configuration.modelId());
            return new DiagnosticEvaluationObservation(testCase.id(), testCase.type(), runNumber, true,
                    List.of(), null, 5, 1, 100, 20, 100);
        };

        List<DiagnosticEvaluationReport> reports = new DiagnosticCandidateEvaluationRunner().run(caseRunner);

        assertThat(reports).extracting(DiagnosticEvaluationReport::modelId)
                .containsExactly("gemini-3.1-flash-lite");
        assertThat(calls).hasValue(45);
        assertThat(modelIds).containsOnly("gemini-3.1-flash-lite");
    }
}
