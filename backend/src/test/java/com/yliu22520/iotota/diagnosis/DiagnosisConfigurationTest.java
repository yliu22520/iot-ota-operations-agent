package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosisConfigurationTest {

    @Test
    void deepSeekRuntimeProviderCannotBeSwitchedAwayFromTheReleaseBaseline() {
        DiagnosticModelConfiguration configuration = new DiagnosisConfiguration()
                .diagnosticModelConfiguration("deepseek");

        assertThat(configuration.modelId()).isEqualTo("deepseek-v4-flash");
        assertThat(configuration.supportsMultimodalInput()).isFalse();
    }
}
