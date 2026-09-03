package com.yliu22520.iotota.diagnosis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes the shared 15-case runner three times per configuration and applies the release gate. */
public final class DiagnosticEvaluationRunner {

    private final DiagnosticEvaluationCaseRunner caseRunner;
    private final DiagnosticReleaseGate releaseGate;

    public DiagnosticEvaluationRunner(DiagnosticEvaluationCaseRunner caseRunner) {
        this(caseRunner, new DiagnosticReleaseGate());
    }

    public DiagnosticEvaluationRunner(DiagnosticEvaluationCaseRunner caseRunner,
                                      DiagnosticReleaseGate releaseGate) {
        this.caseRunner = Objects.requireNonNull(caseRunner, "caseRunner");
        this.releaseGate = Objects.requireNonNull(releaseGate, "releaseGate");
    }

    public DiagnosticEvaluationReport run(DiagnosticModelConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        List<DiagnosticEvaluationObservation> observations = new ArrayList<>();
        for (DiagnosticEvaluationCase testCase : DiagnosticEvaluationCaseCatalog.all()) {
            for (int runNumber = 1; runNumber <= 3; runNumber++) {
                observations.add(runCase(testCase, runNumber, configuration));
            }
        }
        return new DiagnosticEvaluationReport(configuration.provider(), configuration.configurationId(),
                configuration.modelId(),
                configuration.reasoningTier(), configuration.reasoningBudgetTokens(), configuration.promptVersion(),
                configuration.toolSchemaVersion(),
                configuration.limits(), configuration.automaticFallbackEnabled(), DiagnosticEvaluationCaseCatalog.all(),
                observations, releaseGate.evaluate(observations));
    }

    private DiagnosticEvaluationObservation runCase(DiagnosticEvaluationCase testCase,
                                                     int runNumber,
                                                     DiagnosticModelConfiguration configuration) {
        try {
            return caseRunner.run(testCase, runNumber, configuration);
        } catch (DiagnosticBudgetExceededException exception) {
            return failure(testCase, runNumber, "BUDGET_EXCEEDED");
        } catch (DiagnosticModelUnavailableException exception) {
            String failureClass = exception.getMessage() != null
                    && exception.getMessage().startsWith(DiagnosticModelUnavailableException.API_KEY_MISSING)
                    ? "REAL_MODEL_UNAVAILABLE"
                    : "REAL_MODEL_CALL_FAILED";
            return failure(testCase, runNumber, failureClass);
        } catch (RuntimeException exception) {
            return failure(testCase, runNumber, "CASE_RUNNER_FAILED");
        }
    }

    private DiagnosticEvaluationObservation failure(DiagnosticEvaluationCase testCase,
                                                    int runNumber,
                                                    String failureClass) {
        return new DiagnosticEvaluationObservation(testCase.id(), testCase.type(), runNumber, false, List.of(),
                failureClass, 0, 0, 0, 0, 0);
    }
}
