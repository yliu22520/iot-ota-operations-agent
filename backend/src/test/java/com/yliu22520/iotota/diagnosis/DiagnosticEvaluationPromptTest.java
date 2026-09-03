package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticEvaluationPromptTest {

    @Test
    void includesTheAuthoritativeCaseContractForProviderNeutralEvaluation() {
        DiagnosticEvaluationCase testCase = DiagnosticEvaluationCaseCatalog.all().get(1);

        String prompt = DiagnosticEvaluationPrompt.render(testCase, DiagnosticModelConfiguration.releaseBaseline());

        assertThat(prompt).contains(
                "Expected root cause code: `CALLBACK_TIMEOUT`",
                "Retry eligible: `true`",
                "Required assertions: [`RETRY_PLAN_BOUND`, `APPROVAL_REQUIRED`, `IDEMPOTENT_EXECUTION`, `EXECUTION_VERIFIED`]");
        assertThat(prompt).contains("approvalRequired=true when retryEligible=true");
        assertThat(prompt).contains("executionVerified=true when EXECUTION_VERIFIED is required");
        assertThat(prompt).contains("read-only plan labels only");
    }
}
