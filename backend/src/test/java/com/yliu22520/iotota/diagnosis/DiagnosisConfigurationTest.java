package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosisConfigurationTest {

    @Test
    void geminiRuntimeProviderCannotBeSwitchedAwayFromTheReleaseBaseline() {
        DiagnosticModelConfiguration configuration = new DiagnosisConfiguration()
                .diagnosticModelConfiguration("gemini");

        assertThat(configuration.modelId()).isEqualTo("gemini-2.5-flash");
        assertThat(configuration.supportsMultimodalInput()).isFalse();
    }

    @Test
    void geminiRuntimeProviderRejectsAnUnverifiedModelName() {
        assertThatThrownBy(() -> new DiagnosisConfiguration()
                .diagnosticModelConfiguration("gemini", "gemini-2.5-flash-lite"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gemini-2.5-flash");
    }

    @Test
    void geminiRuntimeProviderNormalizesCaseAndWhitespace() {
        DiagnosticModelConfiguration configuration = new DiagnosisConfiguration()
                .diagnosticModelConfiguration(" GEMINI ", " gemini-2.5-flash ");

        assertThat(configuration.provider()).isEqualTo("gemini");
        assertThat(configuration.modelId()).isEqualTo("gemini-2.5-flash");
    }
}
