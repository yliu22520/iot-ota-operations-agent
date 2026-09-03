package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosisConfigurationTest {

    @Test
    void geminiRuntimeProviderCannotBeSwitchedAwayFromTheReleaseBaseline() {
        DiagnosticModelConfiguration configuration = new DiagnosisConfiguration()
                .diagnosticModelConfiguration("gemini");

        assertThat(configuration.modelId()).isEqualTo("gemini-3.1-flash-lite");
        assertThat(configuration.supportsMultimodalInput()).isFalse();
    }

    @Test
    void geminiRuntimeProviderRejectsAnUnverifiedModelName() {
        assertThatThrownBy(() -> new DiagnosisConfiguration()
                .diagnosticModelConfiguration("gemini", "gemini-3.6-flash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gemini-3.1-flash-lite");
    }

    @Test
    void geminiRuntimeProviderNormalizesCaseAndWhitespace() {
        DiagnosticModelConfiguration configuration = new DiagnosisConfiguration()
                .diagnosticModelConfiguration(" GEMINI ", " gemini-3.1-flash-lite ");

        assertThat(configuration.provider()).isEqualTo("gemini");
        assertThat(configuration.modelId()).isEqualTo("gemini-3.1-flash-lite");
    }
}
