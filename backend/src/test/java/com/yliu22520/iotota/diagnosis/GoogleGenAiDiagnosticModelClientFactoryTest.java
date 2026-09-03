package com.yliu22520.iotota.diagnosis;

import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleGenAiDiagnosticModelClientFactoryTest {

    @Test
    void mapsAnOptionalStandardHttpProxyToGoogleClientOptions() {
        ClientOptions options = GoogleGenAiDiagnosticModelClientFactory
                .proxyClientOptions("http://127.0.0.1:7890");

        assertThat(options).isNotNull();
        assertThat(options.proxyOptions()).isPresent();
        assertThat(options.proxyOptions().orElseThrow().host()).contains("127.0.0.1");
        assertThat(options.proxyOptions().orElseThrow().port()).contains(7890);
    }

    @Test
    void doesNotCreateClientOptionsWhenProxyIsNotConfigured() {
        assertThat(GoogleGenAiDiagnosticModelClientFactory.proxyClientOptions(" ")).isNull();
    }

    @Test
    void boundsEachGoogleInteractionToTheDiagnosticBudgetWithoutSdkRetries() {
        HttpOptions options = GoogleGenAiDiagnosticModelClientFactory.httpOptions(
                DiagnosticModelConfiguration.releaseBaseline());

        assertThat(options.timeout()).contains(90_000);
        assertThat(options.retryOptions()).isPresent();
        assertThat(options.retryOptions().orElseThrow().attempts()).contains(1);
    }
}
