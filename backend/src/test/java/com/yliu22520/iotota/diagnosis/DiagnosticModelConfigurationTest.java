package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticModelConfigurationTest {

    @Test
    void releaseBaselinePinsTheModelPromptReasoningAndParameters() {
        DiagnosticModelConfiguration configuration = DiagnosticModelConfiguration.releaseBaseline();

        assertThat(configuration.provider()).isEqualTo("deepseek");
        assertThat(configuration.modelId()).isEqualTo("deepseek-v4-flash");
        assertThat(configuration.reasoningTier()).isEqualTo("HIGH");
        assertThat(configuration.promptVersion()).isEqualTo("diagnosis-agent-v1");
        assertThat(configuration.toolSchemaVersion()).isEqualTo("diagnostic-tools-v1");
        assertThat(configuration.temperature()).isZero();
        assertThat(configuration.topP()).isEqualTo(1.0d);
        assertThat(configuration.limits()).isEqualTo(DiagnosticExecutionLimits.v1());
        assertThat(configuration.automaticFallbackEnabled()).isFalse();
        assertThat(configuration.configurationId())
                .isEqualTo("deepseek:deepseek-v4-flash:HIGH:diagnosis-agent-v1:diagnostic-tools-v1:temperature=0.0:topP=1.0");
    }

    @Test
    void candidateSetIsExactlyTheTwoModelsFromTheReleasePlan() {
        assertThat(DiagnosticModelConfiguration.candidateModelIds())
                .containsExactly("deepseek-v4-flash", "deepseek-v4-pro");
    }

    @Test
    void releaseConfigurationIsPinnedToFlashAndTextOnly() {
        DiagnosticModelConfiguration configuration = DiagnosticModelConfiguration.runtimeRelease();

        assertThat(configuration.modelId()).isEqualTo("deepseek-v4-flash");
        assertThat(configuration.supportsMultimodalInput()).isFalse();
    }
}
