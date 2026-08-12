package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekDiagnosticChatModelTest {

    @Test
    void rendersOnlyStructuredFactsAndKeepsModelTelemetrySeparateFromTheReportText() {
        CapturingClient client = new CapturingClient();
        DeepSeekDiagnosticChatModel model = new DeepSeekDiagnosticChatModel(
                client, DiagnosticModelConfiguration.releaseBaseline());

        DiagnosticExplanation explanation = model.explain(new DiagnosticExplanationRequest(
                new VersionCompatibilityFacts("EDGE-CAMERA-A", "1.0.0", "2.0.0", "RELEASED", "SENSOR-HUB-B"),
                new VersionCompatibilityDecision(false, "MODEL_NOT_SUPPORTED", "model is not supported"),
                List.of(new EvidenceRef("compatibility:101", "backend.rule", Instant.parse("2026-08-12T00:00:00Z"),
                        "backend compatibility result"))));

        assertThat(explanation.text()).isEqualTo("The backend rule is authoritative.");
        assertThat(explanation.inputTokens()).isEqualTo(120);
        assertThat(explanation.outputTokens()).isEqualTo(8);
        assertThat(model.modelId()).isEqualTo("deepseek-v4-flash");
        assertThat(client.request.prompt()).contains("MODEL_NOT_SUPPORTED", "compatibility:101", "HIGH");
        assertThat(client.request.prompt()).doesNotContain("reasoning_content");
    }

    @Test
    void propagatesRealModelFailureInsteadOfUsingTheControlledSubstitute() {
        DeepSeekDiagnosticChatModel model = new DeepSeekDiagnosticChatModel(
                request -> {
                    throw new DiagnosticModelUnavailableException("MISSING_DEEPSEEK_API_KEY");
                }, DiagnosticModelConfiguration.releaseBaseline());

        assertThatThrownBy(() -> model.explain(new DiagnosticExplanationRequest(
                new VersionCompatibilityFacts("EDGE-CAMERA-A", "1.0.0", "2.0.0", "RELEASED", "SENSOR-HUB-B"),
                new VersionCompatibilityDecision(false, "MODEL_NOT_SUPPORTED", "model is not supported"),
                List.of())))
                .isInstanceOf(DiagnosticModelUnavailableException.class)
                .hasMessage("MISSING_DEEPSEEK_API_KEY");
    }

    private static final class CapturingClient implements DiagnosticModelClient {
        private DiagnosticModelRequest request;

        @Override
        public DiagnosticModelResponse complete(DiagnosticModelRequest request) {
            this.request = request;
            return new DiagnosticModelResponse("The backend rule is authoritative.", 120, 8);
        }
    }
}
