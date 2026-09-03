package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticModelConfigurationTest {

    @Test
    void releaseBaselinePinsTheModelPromptReasoningAndParameters() {
        DiagnosticModelConfiguration configuration = DiagnosticModelConfiguration.releaseBaseline();

        assertThat(configuration.provider()).isEqualTo("gemini");
        assertThat(configuration.modelId()).isEqualTo("gemini-3.1-flash-lite");
        assertThat(configuration.reasoningTier()).isEqualTo("HIGH");
        assertThat(configuration.reasoningBudgetTokens()).isEqualTo(4_096);
        assertThat(configuration.promptVersion()).isEqualTo("diagnosis-agent-v2");
        assertThat(configuration.toolSchemaVersion()).isEqualTo("diagnostic-tools-v1");
        assertThat(configuration.temperature()).isZero();
        assertThat(configuration.topP()).isEqualTo(1.0d);
        assertThat(configuration.limits()).isEqualTo(DiagnosticExecutionLimits.v1());
        assertThat(configuration.automaticFallbackEnabled()).isFalse();
        assertThat(configuration.configurationId())
                .isEqualTo("gemini:gemini-3.1-flash-lite:HIGH:diagnosis-agent-v2:diagnostic-tools-v1:"
                        + "reasoningBudgetTokens=4096:temperature=0.0:topP=1.0");
    }

    @Test
    void candidateSetContainsThePinnedGeminiReleaseModel() {
        assertThat(DiagnosticModelConfiguration.candidateModelIds())
                .containsExactly("gemini-3.1-flash-lite");
    }

    @Test
    void releaseConfigurationIsPinnedToFlashAndTextOnly() {
        DiagnosticModelConfiguration configuration = DiagnosticModelConfiguration.runtimeRelease();

        assertThat(configuration.modelId()).isEqualTo("gemini-3.1-flash-lite");
        assertThat(configuration.supportsMultimodalInput()).isFalse();
    }
}
