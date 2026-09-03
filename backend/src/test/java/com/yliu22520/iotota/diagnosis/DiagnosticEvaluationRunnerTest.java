package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticEvaluationRunnerTest {

    @Test
    void runsEveryCaseExactlyThreeTimesAndRecordsThePinnedConfiguration() {
        AtomicInteger calls = new AtomicInteger();
        DiagnosticEvaluationCaseRunner caseRunner = (testCase, runNumber, configuration) -> {
            calls.incrementAndGet();
            return new DiagnosticEvaluationObservation(testCase.id(), testCase.type(), runNumber, true,
                    List.of(), null, 5, 1, 100, 20, 100);
        };

        DiagnosticEvaluationReport report = new DiagnosticEvaluationRunner(caseRunner)
                .run(DiagnosticModelConfiguration.releaseBaseline());

        assertThat(calls).hasValue(45);
        assertThat(report.observations()).hasSize(45);
        assertThat(report.provider()).isEqualTo("gemini");
        assertThat(report.modelId()).isEqualTo("gemini-3.1-flash-lite");
        assertThat(report.reasoningTier()).isEqualTo("HIGH");
        assertThat(report.reasoningBudgetTokens()).isEqualTo(4_096);
        assertThat(report.releaseDecision().releaseAllowed()).isTrue();
    }

    @Test
    void turnsMissingKeyOrRealCallErrorsIntoAnExplicitIncompleteObservation() {
        DiagnosticEvaluationCaseRunner caseRunner = (testCase, runNumber, configuration) -> {
            throw new DiagnosticModelUnavailableException(DiagnosticModelUnavailableException.API_KEY_MISSING);
        };

        DiagnosticEvaluationReport report = new DiagnosticEvaluationRunner(caseRunner)
                .run(DiagnosticModelConfiguration.releaseBaseline());

        assertThat(report.releaseDecision().releaseAllowed()).isFalse();
        assertThat(report.observations()).allSatisfy(observation -> {
            assertThat(observation.passed()).isFalse();
            assertThat(observation.failureClass()).isEqualTo("REAL_MODEL_UNAVAILABLE");
        });
    }
}
